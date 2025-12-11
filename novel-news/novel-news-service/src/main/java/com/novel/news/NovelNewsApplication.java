package com.novel.news;

import com.novel.news.service.NewsService;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(scanBasePackages = "com.novel")
@EnableDiscoveryClient
@MapperScan("com.novel.news.dao.mapper")
@EnableCaching
public class NovelNewsApplication {
    public static void main(String[] args) {
        SpringApplication.run(NovelNewsApplication.class, args);
    }
}
