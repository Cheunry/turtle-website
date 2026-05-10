package com.novel.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 审核流水线执行方式。仅作用于 {@link com.novel.ai.service.impl.TextServiceImpl} 的书籍/章节审核。
 */
@ConfigurationProperties(prefix = "novel.ai.audit")
public class NovelAiAuditExecutionProperties {

    /**
     * 为 true 时，在 {@link java.util.concurrent.Executors#newVirtualThreadPerTaskExecutor()}
     * 上执行 {@link com.novel.ai.agent.core.AuditPipeline}。
     */
    private boolean virtualThreadExecution = true;

    /**
     * 全实例内同时执行的审核 Pipeline 上限（书籍 + 章节共用同一把信号量）。
     * ≤0 表示不限制（内部用极大许可数，等价于不设闸门）。
     */
    private int maxConcurrent = 16;

    public boolean isVirtualThreadExecution() {
        return virtualThreadExecution;
    }

    public void setVirtualThreadExecution(boolean virtualThreadExecution) {
        this.virtualThreadExecution = virtualThreadExecution;
    }

    public int getMaxConcurrent() {
        return maxConcurrent;
    }

    public void setMaxConcurrent(int maxConcurrent) {
        this.maxConcurrent = maxConcurrent;
    }
}
