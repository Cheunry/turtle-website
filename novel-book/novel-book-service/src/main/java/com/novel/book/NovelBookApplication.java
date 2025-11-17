package com.novel.book;

import com.novel.book.manager.feign.ListBookFeign;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients(basePackageClasses = {ListBookFeign.class})
@MapperScan("com.novel.*.dao.mapper")   // 单级目录通配，让单个 @MapperScan 一次性覆盖多个微服务的 mapper 包
public class NovelBookApplication {
    public static void main(String[] args) {
        SpringApplication.run(NovelBookApplication.class, args);
    }
}
