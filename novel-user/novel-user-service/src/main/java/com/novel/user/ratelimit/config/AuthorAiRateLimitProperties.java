package com.novel.user.ratelimit.config;

import com.novel.user.ratelimit.AuthorAiRateLimitScene;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 作家端 AI 接口分布式限流：按场景、按用户 ID 的 Redis 令牌桶。
 * <p>
 * 桶容量 = {@link SceneProperties#getMaxPermits()}，补充速率 = {@code maxPermits / windowSeconds}（令牌/秒）。
 */
@ConfigurationProperties(prefix = "novel.user.ratelimit.author-ai")
public class AuthorAiRateLimitProperties {

    private SceneProperties coverImage = new SceneProperties();
    private SceneProperties audit = new SceneProperties();
    private SceneProperties polish = new SceneProperties();

    public SceneProperties forScene(AuthorAiRateLimitScene scene) {
        return switch (scene) {
            case COVER_IMAGE -> coverImage;
            case AUDIT -> audit;
            case POLISH -> polish;
        };
    }

    public SceneProperties getCoverImage() {
        return coverImage;
    }

    public void setCoverImage(SceneProperties coverImage) {
        this.coverImage = coverImage;
    }

    public SceneProperties getAudit() {
        return audit;
    }

    public void setAudit(SceneProperties audit) {
        this.audit = audit;
    }

    public SceneProperties getPolish() {
        return polish;
    }

    public void setPolish(SceneProperties polish) {
        this.polish = polish;
    }

    public static class SceneProperties {

        /**
         * 关闭则该场景切面直接放行。
         */
        private boolean enabled = true;

        private int maxPermits = 1;

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
}
