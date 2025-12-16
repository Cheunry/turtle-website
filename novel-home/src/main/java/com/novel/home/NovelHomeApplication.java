package com.novel.home;
import com.novel.book.feign.BookFeign;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = {"com.novel"})

@MapperScan("com.novel.*.dao.mapper")   // 单级目录通配，让单个 @MapperScan 一次性覆盖多个微服务的 mapper 包
@EnableFeignClients(basePackageClasses = {BookFeign.class})
@EnableDiscoveryClient
@EnableCaching
public class NovelHomeApplication {
    public static void main(String[] args) {
        SpringApplication.run(NovelHomeApplication.class, args);
    }
}

