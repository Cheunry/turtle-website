package com.novel.ai.model;

/**
 * AI 审核决策的中间结构化输出。
 * <p>
 * 书籍（小说名 + 简介）审核、章节审核都复用这一个结构，由 {@code BeanOutputConverter} 直接反序列化。
 * 字段均使用包装类型，便于在缺失字段时由业务层统一填默认值。
 *
 * @param auditStatus  审核状态：1 = 通过，2 = 不通过；缺失时业务层兜底为 0（待人工审核）
 * @param aiConfidence 置信度 0.0 - 1.0；用 Double 接收以提升 LLM 产出兼容性，业务层再转 BigDecimal
 * @param auditReason  审核原因说明
 */
public record AuditDecisionAiOutput(
        Integer auditStatus,
        Double aiConfidence,
        String auditReason
) {
}
