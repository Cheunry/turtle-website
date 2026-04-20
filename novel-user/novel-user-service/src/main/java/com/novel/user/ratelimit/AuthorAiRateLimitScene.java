package com.novel.user.ratelimit;

/**
 * 作家端 AI 相关接口的用户维度令牌桶场景（与 {@code novel.user.ratelimit.author-ai.*} 配置一一对应）。
 */
public enum AuthorAiRateLimitScene {

    /** AI 封面生图 {@code POST /author/ai/cover} */
    COVER_IMAGE,

    /** AI 提交审核 {@code POST /author/ai/audit} */
    AUDIT,

    /** AI 润色 {@code POST /author/ai/polish} */
    POLISH
}
