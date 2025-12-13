package com.novel.gateway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * **请求/响应日志记录器：Spring Cloud Gateway 全局过滤器**
 *
 * <p>该过滤器实现 {@link GlobalFilter} 和 {@link Ordered} 接口，用于在所有路由的
 * 请求处理生命周期中记录关键的请求和响应信息。
 * 它的主要目的是便于调试、监控和流量追踪。</p>
 *
 * <h3>主要功能:</h3>
 * <ul>
 * <li>在请求到达网关时（Pre-filter 阶段）记录请求路径、方法、头部和查询参数。</li>
 * <li>在响应离开网关时（Post-filter 阶段）记录 HTTP 响应状态码。</li>
 * <li>通过实现 {@link Ordered} 接口并返回 {@link Ordered#HIGHEST_PRECEDENCE}，
 * 确保它作为第一个过滤器执行，完整捕获整个请求生命周期。</li>
 * </ul>
 */
@Component
public class LoggingFilter implements GlobalFilter, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(LoggingFilter.class);

    /**
     * 过滤器核心逻辑：记录请求详情并在请求链完成后记录响应状态。
     *
     * <p>方法分为两部分：
     * <ul>
     * <li>**前置处理 (Pre-filter)**：记录请求信息（路径、方法、头部等）。</li>
     * <li>**后置处理 (Post-filter)**：在调用 {@code chain.filter(exchange)} 后，
     * 使用 {@code .then(Mono.fromRunnable(...))} 记录响应状态码。</li>
     * </ul>
     * </p>
     *
     * @param exchange 服务器 Web 请求上下文，包含了请求和响应对象。
     * @param chain 过滤器链，用于将请求继续传递给下一个过滤器。
     * @return 表示过滤器处理完成的 {@code Mono<Void>}。
     */
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

    /**
     * 定义过滤器在整个过滤链中的执行顺序。
     *
     * @return 返回 {@link Ordered#HIGHEST_PRECEDENCE}，确保该过滤器最先执行。
     */
    @Override
    public int getOrder() {
        // 返回 Ordered.HIGHEST_PRECEDENCE，这意味着这个过滤器拥有最高的优先级，
        // 它会在所有其他过滤器（包括自定义的 GatewayFilter 或其他 GlobalFilter）之前最先执行。
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
