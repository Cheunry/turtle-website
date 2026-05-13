package com.novel.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * token 使用流水异步写库线程池。写库失败不影响模型调用主链路。
 */
@Configuration
public class AiTokenUsageLogExecutorConfiguration {

    @Bean(name = "aiTokenUsageLogExecutor")
    public ThreadPoolTaskExecutor aiTokenUsageLogExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(1);
        exec.setMaxPoolSize(2);
        exec.setQueueCapacity(2000);
        exec.setThreadNamePrefix("ai-token-usage-log-");
        exec.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        exec.initialize();
        return exec;
    }
}
