package com.novel.ai.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 审核经验 RAG 检索的可调参数。用途：
 * <ul>
 *     <li>{@code enabled}=false 时不装配 RAG advisor，LlmStep 直接裸调，便于本地无向量库环境调试；</li>
 *     <li>{@code topK} 和 {@code similarityThreshold} 是召回核心旋钮，
 *         上线初期可设置偏低（topK=5、threshold=0.6）让判例多召回，稳定后再收紧。</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "novel.ai.rag")
public class NovelAiRagProperties {

    /**
     * 是否启用 RAG 增强。关闭时 {@code auditExperienceRagAdvisor} 不会装配，
     * LlmStep 以"纯 Prompt + 结构化输出"模式工作——判例库为空或依赖不可用时的降级开关。
     */
    private boolean enabled = true;

    /**
     * 单次检索返回的最大文档数。
     */
    private int topK = 3;

    /**
     * 相似度阈值（cosine，0~1）。低于此值的候选直接丢弃，
     * 避免"硬凑 topK"把语义不相关的样本塞进 prompt。
     */
    private double similarityThreshold = 0.75;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public int getTopK() { return topK; }
    public void setTopK(int topK) { this.topK = topK; }

    public double getSimilarityThreshold() { return similarityThreshold; }
    public void setSimilarityThreshold(double similarityThreshold) {
        this.similarityThreshold = similarityThreshold;
    }
}
