package com.novel.ai.advisor;

import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.toolkit.trace.ActiveSpan;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.core.Ordered;

/**
 * 模型通信层重试 Advisor——Novel AI "两级重试"架构的外层。
 * <p>
 * 职责切分：
 * <ul>
 *     <li><b>只</b>重试 {@link TransientAiException}（限流 429、模型网关 502/503、网络超时等）。</li>
 *     <li>{@link NonTransientAiException}（内容安全拦截、鉴权失败等）<b>透传不重试</b>——
 *         它们是模型的明确拒绝，重试只会浪费成本。</li>
 *     <li>Entity 解析失败、业务语义错误——在 {@code ChatClient.entity()} 之外，Advisor 链拦不到，
 *         交给 {@code StructuredOutputInvoker} 负责（内层重试 + 修复型 Prompt）。</li>
 * </ul>
 * 采用指数退避策略：{@code initialBackoff * multiplier^(attempt-1)}，
 * 上限由 {@code maxBackoffMs} 控制。
 * <p>
 * SkyWalking 埋点：失败和重试命中都会在当前 Span 打 tag，方便线上链路追踪。
 */
@Slf4j
public class RetryTransientAiAdvisor implements CallAdvisor {

    /**
     * Order 值越低越靠外层。这里用 {@code HIGHEST_PRECEDENCE + 100}，
     * 保证在 QuestionAnswerAdvisor / SimpleLoggerAdvisor 之外，做全链路保护。
     */
    private static final int DEFAULT_ORDER = Ordered.HIGHEST_PRECEDENCE + 100;

    private final NovelAiAdvisorProperties properties;

    public RetryTransientAiAdvisor(NovelAiAdvisorProperties properties) {
        this.properties = properties;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        if (!properties.isRetryEnabled()) {
            return chain.nextCall(request);
        }

        int maxAttempts = Math.max(1, properties.getRetryMaxAttempts());
        TransientAiException lastTransient = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                ChatClientResponse response = chain.nextCall(request);
                if (attempt > 1) {
                    log.info("[RetryAdvisor] 重试成功: attempt={}/{}", attempt, maxAttempts);
                    ActiveSpan.tag("ai.retry.attempts", String.valueOf(attempt));
                    ActiveSpan.tag("ai.retry.outcome", "success_after_retry");
                }
                return response;
            } catch (NonTransientAiException e) {
                log.warn("[RetryAdvisor] 非瞬时异常，透传不重试: {}", e.getMessage());
                ActiveSpan.tag("ai.retry.outcome", "non_transient");
                throw e;
            } catch (TransientAiException e) {
                lastTransient = e;
                if (attempt >= maxAttempts) {
                    log.error("[RetryAdvisor] 已达最大尝试次数仍失败: attempts={}, cause={}", maxAttempts, e.getMessage());
                    ActiveSpan.tag("ai.retry.outcome", "exhausted");
                    ActiveSpan.tag("ai.retry.attempts", String.valueOf(attempt));
                    throw e;
                }
                long backoff = computeBackoffMs(attempt);
                log.warn("[RetryAdvisor] 瞬时异常，{}ms 后重试: attempt={}/{}, cause={}",
                        backoff, attempt, maxAttempts, e.getMessage());
                sleep(backoff);
            }
        }

        // 理论不可达：maxAttempts >= 1 且抛 Transient 会在循环内抛出
        if (lastTransient != null) {
            throw lastTransient;
        }
        throw new IllegalStateException("RetryTransientAiAdvisor exited loop unexpectedly");
    }

    /**
     * 指数退避计算：{@code initial * multiplier^(attempt-1)}，封顶到 {@code maxBackoffMs}。
     */
    private long computeBackoffMs(int attemptFinished) {
        double base = properties.getRetryInitialBackoffMs();
        double multiplier = Math.max(1.0, properties.getRetryBackoffMultiplier());
        double computed = base * Math.pow(multiplier, attemptFinished - 1);
        long capped = Math.min((long) computed, Math.max(properties.getRetryInitialBackoffMs(), properties.getRetryMaxBackoffMs()));
        return Math.max(0L, capped);
    }

    private static void sleep(long ms) {
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Retry backoff sleep interrupted", e);
        }
    }

    @Override
    public String getName() {
        return "NovelAiRetryTransientAdvisor";
    }

    @Override
    public int getOrder() {
        return DEFAULT_ORDER;
    }
}
