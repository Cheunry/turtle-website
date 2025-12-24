package com.novel.search;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.novel"})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = {"com.novel.book.feign"})
@EnableScheduling
public class NovelSearchApplication {

    public static void main(String[] args) {
        SpringApplication.run(NovelSearchApplication.class, args);
    }
}
