package com.novel.gateway;

import com.novel.gateway.config.GatewaySentinelProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * 网关启动类
 */
@SpringBootApplication
@EnableConfigurationProperties(GatewaySentinelProperties.class)
public class NovelGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(NovelGatewayApplication.class);
    }

}
