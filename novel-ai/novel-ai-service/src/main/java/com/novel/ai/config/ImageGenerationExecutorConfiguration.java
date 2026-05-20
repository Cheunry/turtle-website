package com.novel.ai.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
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
    public ThreadPoolTaskExecutor imageGenerationExecutor(
            ImageGenerationExecutorProperties props,
            ObjectProvider<MeterRegistry> meterRegistryProvider) {
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

        meterRegistryProvider.ifAvailable(registry -> bindExecutorMetrics(registry, exec));
        return exec;
    }

    private void bindExecutorMetrics(MeterRegistry registry, ThreadPoolTaskExecutor exec) {
        Gauge.builder("novel.ai.image.executor.active", exec,
                        e -> e.getThreadPoolExecutor().getActiveCount())
                .description("Active image generation worker threads")
                .tag("executor", "imageGeneration")
                .register(registry);
        Gauge.builder("novel.ai.image.executor.pool.size", exec,
                        e -> e.getThreadPoolExecutor().getPoolSize())
                .description("Current image generation thread pool size")
                .tag("executor", "imageGeneration")
                .register(registry);
        Gauge.builder("novel.ai.image.executor.pool.core", exec,
                        e -> e.getThreadPoolExecutor().getCorePoolSize())
                .description("Configured core size of image generation thread pool")
                .tag("executor", "imageGeneration")
                .register(registry);
        Gauge.builder("novel.ai.image.executor.pool.max", exec,
                        e -> e.getThreadPoolExecutor().getMaximumPoolSize())
                .description("Configured max size of image generation thread pool")
                .tag("executor", "imageGeneration")
                .register(registry);
        Gauge.builder("novel.ai.image.executor.queue.size", exec,
                        e -> e.getThreadPoolExecutor().getQueue().size())
                .description("Current image generation queue size")
                .tag("executor", "imageGeneration")
                .register(registry);
        Gauge.builder("novel.ai.image.executor.queue.remaining", exec,
                        e -> e.getThreadPoolExecutor().getQueue().remainingCapacity())
                .description("Remaining image generation queue capacity")
                .tag("executor", "imageGeneration")
                .register(registry);
        Gauge.builder("novel.ai.image.executor.completed", exec,
                        e -> e.getThreadPoolExecutor().getCompletedTaskCount())
                .description("Completed image generation executor task count")
                .tag("executor", "imageGeneration")
                .register(registry);
    }
}
