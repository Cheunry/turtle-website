package com.novel.user.ratelimit.annotation;

import com.novel.user.ratelimit.AuthorAiRateLimitScene;
import com.novel.user.ratelimit.aspect.AuthorAiRateLimitAspect;
import com.novel.user.ratelimit.config.AuthorAiRateLimitProperties;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 作家端 AI 接口：Redis 令牌桶限流（按用户维度）。阈值见 {@link AuthorAiRateLimitProperties}。
 *
 * @see AuthorAiRateLimitAspect
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuthorAiRateLimit {

    AuthorAiRateLimitScene value();
}
