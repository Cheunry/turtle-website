package com.novel.ai.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 生图任务专用执行器：有界队列 + {@link ThreadPoolExecutor.AbortPolicy}，
 * 饱和时立即拒绝，由 {@link com.novel.ai.service.ImageGenerationGate} 转为业务错误码。
 */
@Configuration
@EnableConfigurationProperties(ImageGenerationExecutorProperties.class)
public class ImageGenerationExecutorConfiguration {

    @Bean(name = "imageGenerationExecutor")
    public ThreadPoolTaskExecutor imageGenerationExecutor(ImageGenerationExecutorProperties props) {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(props.getCorePoolSize());
        exec.setMaxPoolSize(props.getMaxPoolSize());
        exec.setQueueCapacity(props.getQueueCapacity());
        exec.setKeepAliveSeconds(props.getKeepAliveSeconds());
        exec.setThreadNamePrefix("ai-image-gen-");
        exec.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        exec.setWaitForTasksToCompleteOnShutdown(true);
        exec.setAwaitTerminationSeconds(90);
        exec.initialize();
        return exec;
    }
}
