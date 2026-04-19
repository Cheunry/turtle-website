package com.novel.user.ratelimit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 封面生图（/author/ai/cover）分布式限流：按用户 ID 的 Redis 令牌桶。
 * <p>
 * 桶容量 = {@link #maxPermits}，补充速率 = {@code maxPermits / windowSeconds}（令牌/秒），
 * 长期平均不超过「每 windowSeconds 秒 maxPermits 次」，并平滑窗口边界突发。
 */
@ConfigurationProperties(prefix = "novel.user.ratelimit.cover-image")
public class CoverImageRateLimitProperties {

    /**
     * 关闭则切面直接放行（便于压测或紧急放开）。
     */
    private boolean enabled = true;

    /**
     * 令牌桶容量（最大突发，与原先「窗口内最大次数」同量级配置）。
     */
    private int maxPermits = 10;

    /**
     * 参考时间窗（秒），与 {@link #maxPermits} 一起决定补充速率 {@code maxPermits/windowSeconds}。
     */
    private long windowSeconds = 60L;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxPermits() {
        return maxPermits;
    }

    public void setMaxPermits(int maxPermits) {
        this.maxPermits = maxPermits;
    }

    public long getWindowSeconds() {
        return windowSeconds;
    }

    public void setWindowSeconds(long windowSeconds) {
        this.windowSeconds = windowSeconds;
    }
}
