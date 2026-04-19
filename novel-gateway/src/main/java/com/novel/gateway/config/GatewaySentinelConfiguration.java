package com.novel.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.common.SentinelGatewayConstants;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.BlockRequestHandler;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * Sentinel Spring Cloud Gateway：按 routeId 的 QPS 限流，阈值来自 {@code novel.gateway.sentinel.routes}。
 */
@Configuration
public class GatewaySentinelConfiguration {

    private static final Logger log = LoggerFactory.getLogger(GatewaySentinelConfiguration.class);

    private static final String BLOCKED_JSON =
        "{\"code\":\"42901\",\"message\":\"请求过于频繁，请稍后再试\",\"data\":null,\"ok\":false}";

    private final GatewaySentinelProperties sentinelProperties;

    public GatewaySentinelConfiguration(GatewaySentinelProperties sentinelProperties) {
        this.sentinelProperties = sentinelProperties;
    }

    @Bean
    @ConditionalOnProperty(prefix = "novel.gateway.sentinel", name = "enabled", havingValue = "true")
    ApplicationRunner sentinelGatewayFlowRulesInitializer() {
        return args -> {
            Map<String, Integer> routeLimits = sentinelProperties.getRoutes();
            if (routeLimits == null || routeLimits.isEmpty()) {
                log.warn("novel.gateway.sentinel.routes 为空，未加载任何 GatewayFlowRule");
                return;
            }
            Set<GatewayFlowRule> rules = new HashSet<>();
            routeLimits.forEach(
                (routeId, qps) -> {
                    if (qps == null || qps <= 0) {
                        log.warn("忽略非法 Sentinel QPS: routeId={}, qps={}", routeId, qps);
                        return;
                    }
                    GatewayFlowRule rule =
                        new GatewayFlowRule(routeId)
                            .setResourceMode(SentinelGatewayConstants.RESOURCE_MODE_ROUTE_ID)
                            .setGrade(RuleConstant.FLOW_GRADE_QPS)
                            .setCount(qps)
                            .setIntervalSec(1);
                    rules.add(rule);
                    log.info("Sentinel 网关限流: routeId={}, qps={}/s", routeId, qps);
                });
            GatewayRuleManager.loadRules(rules);
        };
    }

    @Bean
    @ConditionalOnProperty(prefix = "novel.gateway.sentinel", name = "enabled", havingValue = "true")
    ApplicationRunner sentinelGatewayBlockHandler() {
        return args ->
            GatewayCallbackManager.setBlockHandler(
                (BlockRequestHandler)
                    (exchange, throwable) -> {
                        if (log.isDebugEnabled()) {
                            log.debug(
                                "Sentinel 阻断: {}",
                                throwable != null ? throwable.getMessage() : "");
                        }
                        return ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(BodyInserters.fromValue(BLOCKED_JSON));
                    });
    }
}
