package com.novel.ai.sensitive;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Nacos / Spring Cloud 刷新环境后，{@link SensitiveWordMatcher} 仅在启动时 {@link jakarta.annotation.PostConstruct}
 * 加载过一次；若不重载，会出现「控制台里 enabled 已改但运行期仍走 AI」的假象。
 * <p>
 * 在 {@code novel.ai.sensitive-filter.*} 变更时重建 AC 自动机。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.LOWEST_PRECEDENCE)
public class SensitiveWordConfigChangeListener {

    private static final String PREFIX = "novel.ai.sensitive-filter";

    private final SensitiveWordMatcher sensitiveWordMatcher;

    @EventListener
    public void onEnvironmentChange(EnvironmentChangeEvent event) {
        if (event.getKeys() == null || event.getKeys().isEmpty()) {
            return;
        }
        boolean hit = false;
        for (String key : event.getKeys()) {
            if (key != null && key.startsWith(PREFIX)) {
                hit = true;
                break;
            }
        }
        if (!hit) {
            return;
        }
        log.info("[SensitiveWord] 检测到配置变更 keys={}，重新加载词典", event.getKeys());
        sensitiveWordMatcher.refresh();
    }
}
