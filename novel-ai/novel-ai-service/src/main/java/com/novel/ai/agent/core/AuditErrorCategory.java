package com.novel.ai.agent.core;

/**
 * 审核流水线异常分类。决定业务层应如何把错误翻译为业务响应。
 */
public enum AuditErrorCategory {

    /** AI 模型侧的内容安全拦截（DataInspectionFailed / inappropriate content 等）。 */
    CONTENT_INSPECTION_FAILED,

    /** 其他未分类的异常，通常降级为"待人工审核"。 */
    OTHER
}
