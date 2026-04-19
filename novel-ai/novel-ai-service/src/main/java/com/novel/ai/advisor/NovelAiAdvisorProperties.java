package com.novel.ai.advisor;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Advisor 链相关策略配置。
 * <p>
 * Novel AI 采用"两级重试"架构：
 * <ul>
 *     <li>Advisor 层（本配置）：只重试 {@code TransientAiException}（网络/限流/超时）；</li>
 *     <li>Invoker 层（{@code NovelAiStructuredOutputProperties}）：重试 entity 解析失败，配合"修复型 Prompt"。</li>
 * </ul>
 * 两级职责清晰：Advisor 管模型通信，Invoker 管结构化输出业务语义。
 */
@ConfigurationProperties(prefix = "novel.ai.advisor")
public class NovelAiAdvisorProperties {

    /**
     * Retry Advisor 是否启用。默认开启。
     */
    private boolean retryEnabled = true;

    /**
     * Retry Advisor 最大尝试次数（首次 + 重试总和）。
     */
    private int retryMaxAttempts = 3;

    /**
     * 初始退避毫秒数，每次失败乘以 {@link #retryBackoffMultiplier}。
     */
    private long retryInitialBackoffMs = 300L;

    /**
     * 退避倍数，用于指数退避。
     */
    private double retryBackoffMultiplier = 2.0;

    /**
     * 单次退避上限，防止退避时间爆炸。
     */
    private long retryMaxBackoffMs = 3000L;

    /**
     * 是否向 SkyWalking 写入 LLM 调用埋点（duration / status / token tags）。
     * <p>
     * 注意：控制台 {@code [LlmCall]} 与 token 统计日志由 {@link StructuredOutputLogAdvisor}
     * <b>始终</b>输出（只要发生 ChatClient 调用且日志级别为 INFO），不受本开关影响，
     * 避免误关「观测」后连本地排障日志也消失。
     */
    private boolean observationEnabled = true;

    public boolean isRetryEnabled() {
        return retryEnabled;
    }

    public void setRetryEnabled(boolean retryEnabled) {
        this.retryEnabled = retryEnabled;
    }

    public int getRetryMaxAttempts() {
        return retryMaxAttempts;
    }

    public void setRetryMaxAttempts(int retryMaxAttempts) {
        this.retryMaxAttempts = retryMaxAttempts;
    }

    public long getRetryInitialBackoffMs() {
        return retryInitialBackoffMs;
    }

    public void setRetryInitialBackoffMs(long retryInitialBackoffMs) {
        this.retryInitialBackoffMs = retryInitialBackoffMs;
    }

    public double getRetryBackoffMultiplier() {
        return retryBackoffMultiplier;
    }

    public void setRetryBackoffMultiplier(double retryBackoffMultiplier) {
        this.retryBackoffMultiplier = retryBackoffMultiplier;
    }

    public long getRetryMaxBackoffMs() {
        return retryMaxBackoffMs;
    }

    public void setRetryMaxBackoffMs(long retryMaxBackoffMs) {
        this.retryMaxBackoffMs = retryMaxBackoffMs;
    }

    public boolean isObservationEnabled() {
        return observationEnabled;
    }

    public void setObservationEnabled(boolean observationEnabled) {
        this.observationEnabled = observationEnabled;
    }
}
