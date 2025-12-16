package com.novel.book;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;


@SpringBootApplication(scanBasePackages = {"com.novel"})
@MapperScan("com.novel.book.dao.mapper")
@EnableCaching
@EnableDiscoveryClient
@EnableFeignClients(basePackages = {"com.novel.user.feign"})
public class NovelBookApplication {

    public static void main(String[] args) {
        SpringApplication.run(NovelBookApplication.class, args);
    }

}
