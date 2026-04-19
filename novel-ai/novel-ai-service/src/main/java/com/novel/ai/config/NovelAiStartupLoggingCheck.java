package com.novel.ai.config;

import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 启动后打印 {@code com.novel.ai} 在 Logback 下的生效级别。
 * 使用 {@code System.out}，避免 Nacos 将 {@code com.novel.ai} 打成 WARN 时连本检查也看不到。
 */
@Component
@Order
public class NovelAiStartupLoggingCheck {

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        org.slf4j.Logger probe = LoggerFactory.getLogger("com.novel.ai");
        if (probe instanceof ch.qos.logback.classic.Logger classic) {
            System.out.println("[novel-ai-service] LoggingCheck: logger=com.novel.ai effectiveLevel="
                    + classic.getEffectiveLevel()
                    + " — 若为 WARN/ERROR，请在 Nacos 放宽 logging.level.com.novel.ai 或 root");
        } else {
            System.out.println("[novel-ai-service] LoggingCheck: 非 Logback 后端，跳过 effectiveLevel");
        }
    }
}
