package com.novel.ai.ratelimit.advisor;

import com.novel.ai.ratelimit.AiTokenBucketRateLimiter;
import com.novel.ai.ratelimit.AiTokenRateLimitContext;
import com.novel.ai.ratelimit.AiTokenUsageLogService;
import com.novel.ai.ratelimit.config.AiTokenRateLimitProperties;
import com.novel.common.constant.ErrorCodeEnum;
import com.novel.config.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.toolkit.trace.ActiveSpan;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;

/**
 * 每次真实 LLM 调用的 token 预算限流 Advisor。
 * <p>
 * 调用前按 prompt 估算值 + completion 预留值预扣；普通 call 成功后用 Usage 结算。
 * 流式调用通常拿不到最终 usage，因此只按预估值占用预算。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiTokenRateLimitAdvisor implements CallAdvisor, StreamAdvisor {

    private static final int DEFAULT_ORDER = Ordered.HIGHEST_PRECEDENCE + 450;

    private final AiTokenRateLimitProperties properties;
    private final AiTokenBucketRateLimiter rateLimiter;
    private final AiTokenUsageLogService usageLogService;

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        Reservation reservation = reserve(request);
        long start = System.currentTimeMillis();
        try {
            ChatClientResponse response = chain.nextCall(request);
            Usage usage = usage(response);
            rateLimiter.settle(reservation.result(), usage != null && usage.getTotalTokens() != null
                    ? usage.getTotalTokens().longValue()
                    : null);
            recordSuccess(reservation, usage, System.currentTimeMillis() - start);
            return response;
        } catch (RuntimeException e) {
            rateLimiter.refund(reservation.result());
            recordFailure(reservation, e, System.currentTimeMillis() - start);
            throw e;
        }
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        Reservation reservation = reserve(request);
        long start = System.currentTimeMillis();
        return chain.nextStream(request)
                .doOnComplete(() -> recordSuccess(reservation, null, System.currentTimeMillis() - start))
                .doOnError(e -> {
                    rateLimiter.refund(reservation.result());
                    recordFailure(reservation, e, System.currentTimeMillis() - start);
                })
                .doFinally(signal -> {
                    if (signal == SignalType.CANCEL) {
                        rateLimiter.refund(reservation.result());
                        recordFailure(reservation, new IllegalStateException("stream cancelled"),
                                System.currentTimeMillis() - start);
                    }
                });
    }

    private Reservation reserve(ChatClientRequest request) {
        String model = resolveModel(request);
        long estimated = estimateTokens(request);
        AiTokenBucketRateLimiter.ReserveResult result = rateLimiter.reserve(model, estimated);
        if (!result.allowed()) {
            log.warn("AI token 预算触发限流: model={}, estimatedTokens={}", model, estimated);
            ActiveSpan.tag("ai.token.ratelimit", "blocked");
            ActiveSpan.tag("ai.token.ratelimit.model", model);
            ActiveSpan.tag("ai.token.ratelimit.estimated", String.valueOf(estimated));
            usageLogService.recordAsync(baseCommand(model, AiTokenRateLimitContext.currentScene(), 0L)
                    .estimatedPromptTokens((long) estimatePromptTokens(request.prompt() == null ? "" : request.prompt().getContents()))
                    .reservedCompletionTokens(Math.max(0L, estimated - estimatePromptTokens(request.prompt() == null ? "" : request.prompt().getContents())))
                    .estimatedTotalTokens(estimated)
                    .status(AiTokenUsageLogService.STATUS_RATE_LIMITED)
                    .errorType(BusinessException.class.getSimpleName())
                    .errorMessage(ErrorCodeEnum.AI_SERVICE_RATE_LIMIT.getMessage())
                    .build());
            throw new BusinessException(ErrorCodeEnum.AI_SERVICE_RATE_LIMIT);
        }
        if (!result.disabled()) {
            ActiveSpan.tag("ai.token.ratelimit", result.failOpen() ? "fail_open" : "reserved");
            ActiveSpan.tag("ai.token.ratelimit.model", model);
            ActiveSpan.tag("ai.token.ratelimit.reserved", String.valueOf(result.reservedTokens()));
        }
        String prompt = request.prompt() == null ? "" : request.prompt().getContents();
        int promptTokens = estimatePromptTokens(prompt);
        String scene = AiTokenRateLimitContext.currentScene();
        int completionReserve = resolveCompletionReserve(request, scene, promptTokens);
        return new Reservation(result, scene, model, promptTokens, completionReserve, estimated);
    }

    private String resolveModel(ChatClientRequest request) {
        ChatOptions options = request.prompt() != null ? request.prompt().getOptions() : null;
        String model = options != null ? options.getModel() : null;
        return model == null || model.isBlank() ? properties.getDefaultModel() : model;
    }

    private long estimateTokens(ChatClientRequest request) {
        String prompt = request.prompt() == null ? "" : request.prompt().getContents();
        int promptTokens = estimatePromptTokens(prompt);
        String scene = AiTokenRateLimitContext.currentScene();
        int completionReserve = resolveCompletionReserve(request, scene, promptTokens);
        ActiveSpan.tag("ai.token.ratelimit.scene", scene);
        ActiveSpan.tag("ai.token.ratelimit.prompt.estimated", String.valueOf(promptTokens));
        ActiveSpan.tag("ai.token.ratelimit.completion.reserved", String.valueOf(completionReserve));
        return Math.max(1L, (long) promptTokens + completionReserve);
    }

    private int estimatePromptTokens(String text) {
        if (text == null || text.isBlank()) {
            return 1;
        }
        int ascii = 0;
        int nonAscii = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) <= 127) {
                ascii++;
            } else {
                nonAscii++;
            }
        }
        double raw = nonAscii * 0.7 + ascii / 4.0;
        double factor = Math.max(1.0, properties.getEstimateSafetyFactor());
        return Math.max(1, (int) Math.ceil(raw * factor));
    }

    private int resolveCompletionReserve(ChatClientRequest request, String scene, int promptTokens) {
        ChatOptions options = request.prompt() != null ? request.prompt().getOptions() : null;
        Integer maxTokens = options != null ? options.getMaxTokens() : null;
        if (maxTokens != null && maxTokens > 0) {
            return maxTokens;
        }
        AiTokenRateLimitProperties.SceneEstimateProperties sceneProps = properties.estimateForScene(scene);
        int fixedReserve = sceneProps.getCompletionReserveTokens() > 0
                ? sceneProps.getCompletionReserveTokens()
                : properties.getDefaultCompletionReserveTokens();
        int dynamicReserve = sceneProps.getCompletionInputRatio() > 0
                ? (int) Math.ceil(promptTokens * sceneProps.getCompletionInputRatio())
                : 0;
        int reserve = Math.max(fixedReserve, dynamicReserve);
        reserve = Math.max(Math.max(1, sceneProps.getMinCompletionReserveTokens()), reserve);
        if (sceneProps.getMaxCompletionReserveTokens() > 0) {
            reserve = Math.min(sceneProps.getMaxCompletionReserveTokens(), reserve);
        }
        return reserve;
    }

    private Usage usage(ChatClientResponse response) {
        if (response == null || response.chatResponse() == null || response.chatResponse().getMetadata() == null) {
            return null;
        }
        return response.chatResponse().getMetadata().getUsage();
    }

    private void recordSuccess(Reservation reservation, Usage usage, long durationMs) {
        if (usage == null) {
            log.warn("[AiTokenUsage] 模型调用成功但未返回 usage: scene={}, model={}, estimatedTotal={}, duration={}ms",
                    reservation.scene(), reservation.model(), reservation.estimatedTotalTokens(), durationMs);
        } else {
            log.info("[AiTokenUsage] 模型 token 用量: scene={}, model={}, estimatedTotal={}, actual(in/out/total)={}/{}/{}, duration={}ms",
                    reservation.scene(),
                    reservation.model(),
                    reservation.estimatedTotalTokens(),
                    usage.getPromptTokens(),
                    usage.getCompletionTokens(),
                    usage.getTotalTokens(),
                    durationMs);
        }
        usageLogService.recordAsync(baseCommand(reservation, durationMs)
                .actualPromptTokens(usage != null && usage.getPromptTokens() != null
                        ? usage.getPromptTokens().longValue()
                        : null)
                .actualCompletionTokens(usage != null && usage.getCompletionTokens() != null
                        ? usage.getCompletionTokens().longValue()
                        : null)
                .actualTotalTokens(usage != null && usage.getTotalTokens() != null
                        ? usage.getTotalTokens().longValue()
                        : null)
                .status(AiTokenUsageLogService.STATUS_SUCCESS)
                .build());
    }

    private void recordFailure(Reservation reservation, Throwable error, long durationMs) {
        usageLogService.recordAsync(baseCommand(reservation, durationMs)
                .status(AiTokenUsageLogService.STATUS_FAILED)
                .errorType(error != null ? error.getClass().getSimpleName() : null)
                .errorMessage(error != null ? error.getMessage() : null)
                .build());
    }

    private AiTokenUsageLogService.RecordCommand.RecordCommandBuilder baseCommand(
            Reservation reservation, long durationMs) {
        return baseCommand(reservation.model(), reservation.scene(), durationMs)
                .estimatedPromptTokens((long) reservation.estimatedPromptTokens())
                .reservedCompletionTokens((long) reservation.reservedCompletionTokens())
                .estimatedTotalTokens(reservation.estimatedTotalTokens());
    }

    private AiTokenUsageLogService.RecordCommand.RecordCommandBuilder baseCommand(
            String model, String scene, long durationMs) {
        return AiTokenUsageLogService.RecordCommand.builder()
                .traceId(AiTokenUsageLogService.currentTraceId())
                .scene(scene)
                .model(model)
                .durationMs(durationMs);
    }

    @Override
    public String getName() {
        return "NovelAiTokenRateLimitAdvisor";
    }

    @Override
    public int getOrder() {
        return DEFAULT_ORDER;
    }

    private record Reservation(
            AiTokenBucketRateLimiter.ReserveResult result,
            String scene,
            String model,
            int estimatedPromptTokens,
            int reservedCompletionTokens,
            long estimatedTotalTokens
    ) {
    }
}
