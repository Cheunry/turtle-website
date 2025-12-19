package com.novel.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication(scanBasePackages = {"com.novel"})
@EnableCaching
@EnableDiscoveryClient
@EnableRetry // 开启重试机制
public class NovelAiApplication {
    public static void main(String[] args) {
        SpringApplication.run(NovelAiApplication.class, args);
    }
}
