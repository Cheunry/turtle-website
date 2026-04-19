package com.novel.ai;

import com.novel.ai.config.NovelAiAuditCategoryProperties;
import com.novel.ai.config.NovelAiLearningAuditProperties;
import com.novel.ai.invoker.NovelAiStructuredOutputProperties;
import com.novel.ai.sensitive.SensitiveWordProperties;
import com.novel.ai.tool.NovelAiPolicyProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication(scanBasePackages = {"com.novel"})
@EnableCaching
@EnableDiscoveryClient
@EnableRetry // 开启重试机制
@EnableFeignClients(basePackages = {"com.novel.user.feign", "com.novel.ai.feign"})
@EnableConfigurationProperties({
        NovelAiStructuredOutputProperties.class,
        SensitiveWordProperties.class,
        NovelAiPolicyProperties.class,
        NovelAiAuditCategoryProperties.class,
        NovelAiLearningAuditProperties.class
})
public class NovelAiApplication {
    public static void main(String[] args) {
        SpringApplication.run(NovelAiApplication.class, args);
    }
}
