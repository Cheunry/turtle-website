package com.novel.user;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = {"com.novel"})
@EnableFeignClients(basePackages = "com.novel.book.feign")
@MapperScan("com.novel.user.dao.mapper")
@EnableCaching
@EnableDiscoveryClient
public class NovelUserApplication {
    public static void main(String[] args) {
        SpringApplication.run(NovelUserApplication.class, args);
    }
}


