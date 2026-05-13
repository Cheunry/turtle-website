package com.novel.ai.ratelimit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * AI 模型 token 预算令牌桶配置。
 * <p>
 * 与接口调用次数限流不同，这里按每次真实 LLM 调用的预计 token 成本预扣，
 * 再用模型返回的 usage 做差额结算。
 */
@ConfigurationProperties(prefix = "novel.ai.token-ratelimit")
public class AiTokenRateLimitProperties {

    private boolean enabled = true;

    /**
     * Redis 脚本异常时是否放行。生产建议 true，避免 Redis 抖动导致 AI 能力整体不可用。
     */
    private boolean failOpen = true;

    /**
     * ChatOptions 未显式携带模型名时使用的桶名。
     */
    private String defaultModel = "default-text-model";

    /**
     * 没有 maxTokens 配置时，预留的输出 token 数。
     */
    private int defaultCompletionReserveTokens = 1024;

    /**
     * 预估 prompt token 时的安全系数，避免中文/标点/工具定义造成低估。
     */
    private double estimateSafetyFactor = 1.25;

    private Map<String, ModelBucketProperties> models = new HashMap<>();

    private Map<String, SceneEstimateProperties> scenes = new HashMap<>();

    public ModelBucketProperties bucketForModel(String model) {
        ModelBucketProperties exact = models.get(model);
        if (exact != null) {
            return exact;
        }
        return models.getOrDefault(defaultModel, new ModelBucketProperties());
    }

    public SceneEstimateProperties estimateForScene(String scene) {
        SceneEstimateProperties exact = scenes.get(scene);
        if (exact != null) {
            return exact;
        }
        return scenes.getOrDefault("default", new SceneEstimateProperties());
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isFailOpen() {
        return failOpen;
    }

    public void setFailOpen(boolean failOpen) {
        this.failOpen = failOpen;
    }

    public String getDefaultModel() {
        return defaultModel;
    }

    public void setDefaultModel(String defaultModel) {
        this.defaultModel = defaultModel;
    }

    public int getDefaultCompletionReserveTokens() {
        return defaultCompletionReserveTokens;
    }

    public void setDefaultCompletionReserveTokens(int defaultCompletionReserveTokens) {
        this.defaultCompletionReserveTokens = defaultCompletionReserveTokens;
    }

    public double getEstimateSafetyFactor() {
        return estimateSafetyFactor;
    }

    public void setEstimateSafetyFactor(double estimateSafetyFactor) {
        this.estimateSafetyFactor = estimateSafetyFactor;
    }

    public Map<String, ModelBucketProperties> getModels() {
        return models;
    }

    public void setModels(Map<String, ModelBucketProperties> models) {
        this.models = models;
    }

    public Map<String, SceneEstimateProperties> getScenes() {
        return scenes;
    }

    public void setScenes(Map<String, SceneEstimateProperties> scenes) {
        this.scenes = scenes;
    }

    public static class ModelBucketProperties {

        private boolean enabled = true;

        /**
         * 桶容量，代表该模型允许突发消耗的 token 数。
         */
        private long capacityTokens = 120_000L;

        /**
         * 预算窗口。补充速率 = capacityTokens / windowSeconds。
         */
        private long windowSeconds = 60L;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getCapacityTokens() {
            return capacityTokens;
        }

        public void setCapacityTokens(long capacityTokens) {
            this.capacityTokens = capacityTokens;
        }

        public long getWindowSeconds() {
            return windowSeconds;
        }

        public void setWindowSeconds(long windowSeconds) {
            this.windowSeconds = windowSeconds;
        }
    }

    public static class SceneEstimateProperties {

        /**
         * 固定输出预留。审核类输出短，通常配置 512~1024；润色类可配 1024 作为下限。
         */
        private int completionReserveTokens = -1;

        /**
         * 输出相对输入 prompt token 的比例。润色输出接近原文，可配置 1.0~1.2；审核类保持 0。
         */
        private double completionInputRatio = 0.0;

        /**
         * 动态输出预留下限。
         */
        private int minCompletionReserveTokens = 1;

        /**
         * 动态输出预留上限。小于等于 0 表示不额外封顶。
         */
        private int maxCompletionReserveTokens = 0;

        public int getCompletionReserveTokens() {
            return completionReserveTokens;
        }

        public void setCompletionReserveTokens(int completionReserveTokens) {
            this.completionReserveTokens = completionReserveTokens;
        }

        public double getCompletionInputRatio() {
            return completionInputRatio;
        }

        public void setCompletionInputRatio(double completionInputRatio) {
            this.completionInputRatio = completionInputRatio;
        }

        public int getMinCompletionReserveTokens() {
            return minCompletionReserveTokens;
        }

        public void setMinCompletionReserveTokens(int minCompletionReserveTokens) {
            this.minCompletionReserveTokens = minCompletionReserveTokens;
        }

        public int getMaxCompletionReserveTokens() {
            return maxCompletionReserveTokens;
        }

        public void setMaxCompletionReserveTokens(int maxCompletionReserveTokens) {
            this.maxCompletionReserveTokens = maxCompletionReserveTokens;
        }
    }
}
