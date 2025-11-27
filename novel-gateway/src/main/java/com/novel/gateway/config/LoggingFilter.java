package com.novel.gateway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class LoggingFilter implements GlobalFilter, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(LoggingFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        // 记录请求进入 Gateway
        logger.info("=== GATEWAY REQUEST ===");
        logger.info("Path: {}", exchange.getRequest().getPath());
        logger.info("Method: {}", exchange.getRequest().getMethod());
        logger.info("Headers: {}", exchange.getRequest().getHeaders());
        logger.info("QueryParams: {}", exchange.getRequest().getQueryParams());

        // 继续处理请求
        return chain.filter(exchange).then(Mono.fromRunnable(() -> {

            // 记录响应
            logger.info("=== GATEWAY RESPONSE ===");
            logger.info("Status: {}", exchange.getResponse().getStatusCode());
            logger.info("From Gateway: true");

        }));
    }

    @Override
    public int getOrder() {

        return Ordered.HIGHEST_PRECEDENCE; // 高优先级，最先执行
    }
}
