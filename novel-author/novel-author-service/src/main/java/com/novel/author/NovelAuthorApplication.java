package com.novel.author;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = {"com.novel"})
@MapperScan("com.novel.author.dao.mapper")
@EnableCaching
@EnableDiscoveryClient
@EnableFeignClients(basePackages = {"com.novel.book.feign"})
public class NovelAuthorApplication {

    public static void main(String[] args) {
        SpringApplication.run(NovelAuthorApplication.class, args);
    }

}

