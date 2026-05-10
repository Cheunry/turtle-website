package com.novel.ai.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

/**
 * 书籍/章节审核流水线专用执行器：novel-ai-service 内<b>唯一</b>使用
 * {@link Executors#newVirtualThreadPerTaskExecutor()} 之处（Tomcat 已
 * {@code spring.threads.virtual.enabled=false}）。见 {@link com.novel.ai.service.impl.TextServiceImpl#runAuditPipeline}。
 * <p>
 * 审核并发上限见 {@link NovelAiAuditExecutionProperties#getMaxConcurrent()} 与
 * {@link #auditConcurrencySemaphore(NovelAiAuditExecutionProperties)}。
 */
@Configuration
@EnableConfigurationProperties(NovelAiAuditExecutionProperties.class)
public class AuditPipelineExecutorConfig {

    public static final String AUDIT_PIPELINE_EXECUTOR = "auditPipelineExecutor";

    public static final String AUDIT_CONCURRENCY_SEMAPHORE = "auditConcurrencySemaphore";

    /**
     * 限制同时进入 {@code AuditPipeline.execute} 的任务数；与虚拟线程执行器叠加使用：
     * 先 {@link Semaphore#acquire()}，再 {@code runAsync(...).join()}，最后在 {@code finally} 中 {@code release()}。
     */
    @Bean(name = AUDIT_CONCURRENCY_SEMAPHORE)
    public Semaphore auditConcurrencySemaphore(NovelAiAuditExecutionProperties properties) {
        int permits = properties.getMaxConcurrent();
        if (permits <= 0) {
            return new Semaphore(Integer.MAX_VALUE);
        }
        return new Semaphore(permits);
    }

    @Bean(name = AUDIT_PIPELINE_EXECUTOR, destroyMethod = "shutdown")
    @ConditionalOnProperty(prefix = "novel.ai.audit", name = "virtual-thread-execution", havingValue = "true", matchIfMissing = true)
    public ExecutorService auditPipelineVirtualExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean(name = AUDIT_PIPELINE_EXECUTOR)
    @ConditionalOnProperty(prefix = "novel.ai.audit", name = "virtual-thread-execution", havingValue = "false")
    public Executor auditPipelineDirectExecutor() {
        return command -> command.run();
    }
}
