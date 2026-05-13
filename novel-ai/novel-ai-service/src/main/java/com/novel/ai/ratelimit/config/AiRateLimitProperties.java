package com.novel.ai.ratelimit.config;

import com.novel.ai.ratelimit.AiRateLimitScene;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 服务内部全局令牌桶限流配置。
 * <p>
 * 桶容量 = {@link SceneProperties#getMaxPermits()}，补充速率 = {@code maxPermits / windowSeconds}（令牌/秒）。
 */
@ConfigurationProperties(prefix = "novel.ai.ratelimit")
public class AiRateLimitProperties {

    private SceneProperties auditBook = new SceneProperties(30, 60L);
    private SceneProperties auditChapter = new SceneProperties(60, 60L);
    private SceneProperties coverPrompt = new SceneProperties(60, 60L);
    private SceneProperties polish = new SceneProperties(120, 60L);
    private SceneProperties polishStream = new SceneProperties(60, 60L);
    private SceneProperties imageGenerate = new SceneProperties(30, 60L);
    private SceneProperties auditRuleExtract = new SceneProperties(30, 60L);

    public SceneProperties forScene(AiRateLimitScene scene) {
        return switch (scene) {
            case AUDIT_BOOK -> auditBook;
            case AUDIT_CHAPTER -> auditChapter;
            case COVER_PROMPT -> coverPrompt;
            case POLISH -> polish;
            case POLISH_STREAM -> polishStream;
            case IMAGE_GENERATE -> imageGenerate;
            case AUDIT_RULE_EXTRACT -> auditRuleExtract;
        };
    }

    public SceneProperties getAuditBook() {
        return auditBook;
    }

    public void setAuditBook(SceneProperties auditBook) {
        this.auditBook = auditBook;
    }

    public SceneProperties getAuditChapter() {
        return auditChapter;
    }

    public void setAuditChapter(SceneProperties auditChapter) {
        this.auditChapter = auditChapter;
    }

    public SceneProperties getCoverPrompt() {
        return coverPrompt;
    }

    public void setCoverPrompt(SceneProperties coverPrompt) {
        this.coverPrompt = coverPrompt;
    }

    public SceneProperties getPolish() {
        return polish;
    }

    public void setPolish(SceneProperties polish) {
        this.polish = polish;
    }

    public SceneProperties getPolishStream() {
        return polishStream;
    }

    public void setPolishStream(SceneProperties polishStream) {
        this.polishStream = polishStream;
    }

    public SceneProperties getImageGenerate() {
        return imageGenerate;
    }

    public void setImageGenerate(SceneProperties imageGenerate) {
        this.imageGenerate = imageGenerate;
    }

    public SceneProperties getAuditRuleExtract() {
        return auditRuleExtract;
    }

    public void setAuditRuleExtract(SceneProperties auditRuleExtract) {
        this.auditRuleExtract = auditRuleExtract;
    }

    public static class SceneProperties {

        /**
         * 关闭则该场景切面直接放行。
         */
        private boolean enabled = true;

        private int maxPermits = 60;

        private long windowSeconds = 60L;

        public SceneProperties() {
        }

        public SceneProperties(int maxPermits, long windowSeconds) {
            this.maxPermits = maxPermits;
            this.windowSeconds = windowSeconds;
        }

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
