package com.novel.ai.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 书籍/章节审核流水线专用执行器：novel-ai-service 内<b>唯一</b>使用
 * {@link Executors#newVirtualThreadPerTaskExecutor()} 之处（Tomcat 已
 * {@code spring.threads.virtual.enabled=false}）。见 {@link com.novel.ai.service.impl.TextServiceImpl#runAuditPipeline}。
 */
@Configuration
@EnableConfigurationProperties(NovelAiAuditExecutionProperties.class)
public class AuditPipelineExecutorConfig {

    public static final String AUDIT_PIPELINE_EXECUTOR = "auditPipelineExecutor";

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
