package com.novel.ai.ratelimit.annotation;

import com.novel.ai.ratelimit.AiRateLimitScene;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * novel-ai-service 内部 Redis 令牌桶限流，按场景做全局 QPS 保护。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AiRateLimit {

    AiRateLimitScene value();
}
