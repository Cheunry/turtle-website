package com.novel.user.ratelimit.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记「AI 封面生图」接口需做 Redis 令牌桶限流（按用户维度）。
 * <p>
 * 具体阈值由 {@link com.novel.user.ratelimit.config.CoverImageRateLimitProperties} 配置，
 * 避免在 Controller / Service 内手写计数逻辑。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CoverImageRateLimit {
}
