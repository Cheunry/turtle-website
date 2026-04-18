package com.novel.ai.advisor;

import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.toolkit.trace.ActiveSpan;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.core.Ordered;

/**
 * 单次 LLM 调用的日志 + SkyWalking 埋点 Advisor。
 * <p>
 * 负责：
 * <ul>
 *     <li>每次 {@code ChatClient} 调用前后的 INFO 日志（耗时 / 模型 / token 统计）；</li>
 *     <li>SkyWalking Span tag：{@code ai.call.duration.ms} / {@code ai.call.status} /
 *         {@code ai.call.token.input|output|total}——交给链路追踪系统；</li>
 *     <li>异常时把异常类名和摘要打到 span，便于线上排查。</li>
 * </ul>
 * 这些指标补充 Spring AI 1.0 官方 Observation（{@code gen_ai.client.*}）里的**应用语义维度**，
 * 两者配合形成完整观测。
 * <p>
 * Order 设计：排在 {@link RetryTransientAiAdvisor} 之内、但在其他业务 Advisor（QA/Tier）之外，
 * 这样日志能准确反映"单次 LLM 实际调用"（含重试的每一次都会进这里打一条）。
 */
@Slf4j
public class StructuredOutputLogAdvisor implements CallAdvisor {

    /**
     * 排在 Retry 之内（order 更高），这样每次重试的单次调用都会被记录。
     */
    private static final int DEFAULT_ORDER = Ordered.HIGHEST_PRECEDENCE + 500;

    private final NovelAiAdvisorProperties properties;

    public StructuredOutputLogAdvisor(NovelAiAdvisorProperties properties) {
        this.properties = properties;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        if (!properties.isObservationEnabled()) {
            return chain.nextCall(request);
        }

        long start = System.currentTimeMillis();
        try {
            ChatClientResponse response = chain.nextCall(request);
            long cost = System.currentTimeMillis() - start;

            ActiveSpan.tag("ai.call.duration.ms", String.valueOf(cost));
            ActiveSpan.tag("ai.call.status", "success");

            ChatResponse chatResponse = response.chatResponse();
            if (chatResponse != null && chatResponse.getMetadata() != null) {
                Usage usage = chatResponse.getMetadata().getUsage();
                if (usage != null) {
                    log.info("[LlmCall] 调用成功: cost={}ms, tokens(in/out/total)={}/{}/{}",
                            cost,
                            nullSafe(usage.getPromptTokens()),
                            nullSafe(usage.getCompletionTokens()),
                            nullSafe(usage.getTotalTokens()));
                    tagTokens(usage);
                } else {
                    log.info("[LlmCall] 调用成功: cost={}ms", cost);
                }
            } else {
                log.info("[LlmCall] 调用成功（无 metadata）: cost={}ms", cost);
            }
            return response;
        } catch (RuntimeException e) {
            long cost = System.currentTimeMillis() - start;
            ActiveSpan.tag("ai.call.duration.ms", String.valueOf(cost));
            ActiveSpan.tag("ai.call.status", "failure");
            ActiveSpan.tag("ai.call.error.type", e.getClass().getSimpleName());
            log.warn("[LlmCall] 调用失败: cost={}ms, error={}: {}",
                    cost, e.getClass().getSimpleName(), e.getMessage());
            throw e;
        }
    }

    private static void tagTokens(Usage usage) {
        if (usage.getPromptTokens() != null) {
            ActiveSpan.tag("ai.call.token.input", String.valueOf(usage.getPromptTokens()));
        }
        if (usage.getCompletionTokens() != null) {
            ActiveSpan.tag("ai.call.token.output", String.valueOf(usage.getCompletionTokens()));
        }
        if (usage.getTotalTokens() != null) {
            ActiveSpan.tag("ai.call.token.total", String.valueOf(usage.getTotalTokens()));
        }
    }

    private static Object nullSafe(Object v) {
        return v == null ? "?" : v;
    }

    @Override
    public String getName() {
        return "NovelAiStructuredOutputLogAdvisor";
    }

    @Override
    public int getOrder() {
        return DEFAULT_ORDER;
    }
}
