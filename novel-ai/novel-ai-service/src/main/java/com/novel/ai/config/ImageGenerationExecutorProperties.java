package com.novel.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 生图专用线程池：限制并发与排队长度，队列满时快速拒绝，避免无限堆积 Tomcat 线程。
 */
@ConfigurationProperties(prefix = "novel.ai.image-gen")
public class ImageGenerationExecutorProperties {

    /**
     * 同时执行生图的工作线程数（建议与 DashScope 配额、机器核数综合评估）。
     */
    private int corePoolSize = 3;

    /**
     * 与 core 一致即为固定大小池。
     */
    private int maxPoolSize = 3;

    /**
     * 等待执行的任务队列长度；已满且线程全忙时 {@code submit} 立即失败。
     */
    private int queueCapacity = 20;

    /**
     * 线程空闲存活秒数（当 core &lt; max 时有效；固定池可忽略）。
     */
    private int keepAliveSeconds = 60;

    /**
     * 单任务从提交到返回的最长等待（须与网关/Feign 超时链协调，略小于下游 read-timeout）。
     */
    private long awaitTimeoutMs = 175_000L;

    public int getCorePoolSize() {
        return corePoolSize;
    }

    public void setCorePoolSize(int corePoolSize) {
        this.corePoolSize = corePoolSize;
    }

    public int getMaxPoolSize() {
        return maxPoolSize;
    }

    public void setMaxPoolSize(int maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }

    public int getKeepAliveSeconds() {
        return keepAliveSeconds;
    }

    public void setKeepAliveSeconds(int keepAliveSeconds) {
        this.keepAliveSeconds = keepAliveSeconds;
    }

    public long getAwaitTimeoutMs() {
        return awaitTimeoutMs;
    }

    public void setAwaitTimeoutMs(long awaitTimeoutMs) {
        this.awaitTimeoutMs = awaitTimeoutMs;
    }
}
