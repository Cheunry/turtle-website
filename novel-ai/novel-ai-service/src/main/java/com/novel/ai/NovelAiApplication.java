package com.novel.ai;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(scanBasePackages = {"com.novel"})
@EnableCaching
@EnableDiscoveryClient
public class NovelAiApplication {
}
