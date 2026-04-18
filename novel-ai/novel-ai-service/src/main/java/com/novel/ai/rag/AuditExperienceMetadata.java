package com.novel.ai.rag;

/**
 * 审核经验向量库（Spring AI {@code ElasticsearchVectorStore}）的 Document metadata key 契约。
 * <p>
 * 统一定义后，阶段 2-B 的 {@code AuditExperienceIndexer}（写入）和
 * 阶段 2-C 的 {@code AuditExperienceRetriever}（召回）必须使用同一组 key，
 * 否则 {@code FilterExpression} / 前端回显会找不到字段。
 * <p>
 * Spring AI 的 {@code Document} 结构是：
 * <pre>
 *   Document(id, content, metadata: Map&lt;String,Object&gt;, embedding)
 * </pre>
 * 其中：
 * <ul>
 *     <li>{@code content}——用于向量化的文本（判例核心片段 + 违规标签 + 规则总结拼接），
 *         和 novel-search 老索引 {@code key_snippet} 相比会更富语义；</li>
 *     <li>{@code metadata}——保留原始业务字段（见下文常量），检索后交给 Prompt 组装器；</li>
 *     <li>{@code embedding}——由 DashScope {@code text-embedding-v4} 生成（1024 维 cosine）。</li>
 * </ul>
 * 索引名：{@code novel_ai_audit_experiences}（见 {@code application.properties} 的
 * {@code spring.ai.vectorstore.elasticsearch.index-name}）。
 */
public final class AuditExperienceMetadata {

    /** 原 {@code audit_content} 表主键，用于幂等 upsert 和链路溯源。 */
    public static final String AUDIT_ID = "audit_id";

    /** 关联书籍 ID（若是章节维度经验，章节 ID 另存 {@link #CHAPTER_ID}）。 */
    public static final String BOOK_ID = "book_id";

    /** 关联章节 ID；书籍级判例此字段可缺失。 */
    public static final String CHAPTER_ID = "chapter_id";

    /** 审核结果：{@code 1=通过}，{@code 2=不通过}，{@code 0=待定（一般不入库）}。 */
    public static final String AUDIT_STATUS = "audit_status";

    /** 违规标签（暴力/色情/政治……），未命中为 {@code null} 或空串；空的不入库。 */
    public static final String VIOLATION_LABEL = "violation_label";

    /** 判例规则总结，作为给大模型的"判例规则"提示文本。 */
    public static final String AUDIT_RULE = "audit_rule";

    /** 核心争议片段原文，作为召回后给模型的"判例证据"。 */
    public static final String KEY_SNIPPET = "key_snippet";

    /** AI 生成的置信度（0.0~1.0）。 */
    public static final String CONFIDENCE = "confidence";

    /** 经验产生时间（毫秒时间戳），用于时间衰减或近似去重。 */
    public static final String CREATED_AT_MS = "created_at_ms";

    /**
     * 判例来源类型：{@code book}/{@code chapter}/{@code chapter_segment}，
     * {@code FilterExpression} 可以按来源过滤（例如只召回章节级判例）。
     */
    public static final String SOURCE_TYPE = "source_type";

    public static final String SOURCE_TYPE_BOOK = "book";
    public static final String SOURCE_TYPE_CHAPTER = "chapter";
    public static final String SOURCE_TYPE_CHAPTER_SEGMENT = "chapter_segment";

    private AuditExperienceMetadata() {
    }
}
