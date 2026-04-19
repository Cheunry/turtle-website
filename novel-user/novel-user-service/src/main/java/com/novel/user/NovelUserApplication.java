package com.novel.user;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import com.novel.user.ratelimit.config.CoverImageRateLimitProperties;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = {"com.novel"})
@EnableFeignClients(basePackages = {"com.novel.book.feign", "com.novel.ai.feign"})
@MapperScan("com.novel.user.dao.mapper")
@EnableCaching
@EnableDiscoveryClient
@EnableConfigurationProperties(CoverImageRateLimitProperties.class)
public class NovelUserApplication {
    public static void main(String[] args) {
        SpringApplication.run(NovelUserApplication.class, args);
    }
}


