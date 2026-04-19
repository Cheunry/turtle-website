package com.novel.gateway.config;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 网关 Sentinel 路由级 QPS，对应 {@code spring.cloud.gateway.routes[].id}。
 */
@ConfigurationProperties(prefix = "novel.gateway.sentinel")
public class GatewaySentinelProperties {

    private boolean enabled = true;

    private Map<String, Integer> routes = new LinkedHashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Map<String, Integer> getRoutes() {
        return routes;
    }

    public void setRoutes(Map<String, Integer> routes) {
        this.routes = routes;
    }
}
