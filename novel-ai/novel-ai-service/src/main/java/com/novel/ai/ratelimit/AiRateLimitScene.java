package com.novel.ai.ratelimit;

/**
 * novel-ai-service 内部资源维度限流场景。
 */
public enum AiRateLimitScene {

    /** 书籍审核 */
    AUDIT_BOOK,

    /** 章节审核 */
    AUDIT_CHAPTER,

    /** 封面提示词生成 */
    COVER_PROMPT,

    /** 文本润色 */
    POLISH,

    /** 文本润色 SSE */
    POLISH_STREAM,

    /** 生图提交 */
    IMAGE_GENERATE,

    /** 审核规则提取 */
    AUDIT_RULE_EXTRACT
}
