package com.novel.ai.invoker;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 结构化输出调用策略配置。
 * <p>
 * 所有字段都给了合理默认值，项目即开即用；需要微调时在 Nacos / application.yml 配置
 * {@code novel.ai.structured-output.*} 即可，无需重启服务（配合 {@code @RefreshScope} 后续再加）。
 */
@ConfigurationProperties(prefix = "novel.ai.structured-output")
public class NovelAiStructuredOutputProperties {

    /**
     * 最大调用尝试次数（首次 + 重试的总次数）。默认 2：首次 + 1 次修复重试。
     */
    private int maxAttempts = 2;

    /**
     * 重试时是否使用"修复型 Prompt"（在 system prompt 后追加严格 JSON 指令）。
     */
    private boolean retryUseRepairPrompt = true;

    /**
     * 重试时是否把上一次的错误消息带进 Prompt，便于模型定位自己错在哪里。
     */
    private boolean includeLastErrorInRetryPrompt = true;

    /**
     * 附带到 Prompt 的错误消息最大长度，超出会截断，避免把半个 stacktrace 塞进去。
     */
    private int errorMessageMaxLength = 200;

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public boolean isRetryUseRepairPrompt() {
        return retryUseRepairPrompt;
    }

    public void setRetryUseRepairPrompt(boolean retryUseRepairPrompt) {
        this.retryUseRepairPrompt = retryUseRepairPrompt;
    }

    public boolean isIncludeLastErrorInRetryPrompt() {
        return includeLastErrorInRetryPrompt;
    }

    public void setIncludeLastErrorInRetryPrompt(boolean includeLastErrorInRetryPrompt) {
        this.includeLastErrorInRetryPrompt = includeLastErrorInRetryPrompt;
    }

    public int getErrorMessageMaxLength() {
        return errorMessageMaxLength;
    }

    public void setErrorMessageMaxLength(int errorMessageMaxLength) {
        this.errorMessageMaxLength = errorMessageMaxLength;
    }
}
