package com.novel.message;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(scanBasePackages = "com.novel")
@EnableDiscoveryClient
@EnableCaching
public class NovelMessageApplication {
    public static void main(String[] args) {
        SpringApplication.run(NovelMessageApplication.class, args);
    }

}
