package com.novel.ai.model;

/**
 * AI 审核判例规则抽取的中间结构化输出，用于沉淀到 RAG 知识库。
 *
 * @param violationLabel 违规/争议标签
 * @param keySnippet     核心争议片段（来源于原文）
 * @param auditRule      判例规则总结（可复用的一句话判例）
 */
public record AuditRuleAiOutput(
        String violationLabel,
        String keySnippet,
        String auditRule
) {
}
