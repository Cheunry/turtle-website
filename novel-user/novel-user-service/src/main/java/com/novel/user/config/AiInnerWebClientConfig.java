package com.novel.user.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 通过服务发现调用 novel-ai 内部接口（如润色 SSE）。
 */
@Configuration
public class AiInnerWebClientConfig {

    @Bean
    @LoadBalanced
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }

    @Bean
    public WebClient aiInnerWebClient(WebClient.Builder webClientBuilder) {
        return webClientBuilder.build();
    }
}
