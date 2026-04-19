package com.novel.user.ratelimit.aspect;

import com.novel.common.auth.UserHolder;
import com.novel.common.constant.ErrorCodeEnum;
import com.novel.common.resp.RestResp;
import com.novel.user.ratelimit.config.CoverImageRateLimitProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * AI 封面生图：Redis 令牌桶限流（Hash + Lua，{@code TIME} 驱动补充），超限返回 {@link ErrorCodeEnum#AI_IMAGE_GENERATION_BUSY}。
 */
@Slf4j
@Aspect
@Component
@Order(10)
@RequiredArgsConstructor
public class CoverImageRateLimitAspect {

    /**
     * 与历史「固定窗口」STRING 计数 key 区分，避免 WRONGTYPE；多实例共享同桶状态。
     */
    private static final String KEY_PREFIX = "ratelimit:{author:ai:cover}:tb:user:";

    /**
     * KEYS[1]：Hash（tok, ts）；ARGV[1]：容量；ARGV[2]：每秒补充令牌数（maxPermits/windowSeconds）。
     */
    private static final DefaultRedisScript<Long> TOKEN_BUCKET_SCRIPT = new DefaultRedisScript<>();

    static {
        TOKEN_BUCKET_SCRIPT.setResultType(Long.class);
        TOKEN_BUCKET_SCRIPT.setScriptText("""
                local capacity = tonumber(ARGV[1])
                local refill_per_sec = tonumber(ARGV[2])
                if capacity == nil or capacity < 1 or refill_per_sec == nil or refill_per_sec <= 0 then
                  return -1
                end
                local t = redis.call('TIME')
                local now = tonumber(t[1]) + tonumber(t[2]) / 1000000.0
                local hm = redis.call('HMGET', KEYS[1], 'tok', 'ts')
                local tok = tonumber(hm[1])
                local ts = tonumber(hm[2])
                if tok == nil then
                  tok = capacity
                  ts = now
                end
                local elapsed = now - ts
                if elapsed < 0 then
                  elapsed = 0
                end
                tok = math.min(capacity, tok + elapsed * refill_per_sec)
                local allowed = 0
                if tok >= 1.0 then
                  tok = tok - 1.0
                  allowed = 1
                end
                ts = now
                redis.call('HSET', KEYS[1], 'tok', tok, 'ts', ts)
                local idle_full = capacity / refill_per_sec
                local ttl = math.ceil(math.max(120, math.min(604800, idle_full * 3)))
                redis.call('EXPIRE', KEYS[1], ttl)
                return allowed
                """);
    }

    private final StringRedisTemplate stringRedisTemplate;
    private final CoverImageRateLimitProperties properties;

    @Around("@annotation(com.novel.user.ratelimit.annotation.CoverImageRateLimit)")
    public Object aroundCoverImage(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!properties.isEnabled()) {
            return joinPoint.proceed();
        }
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            return joinPoint.proceed();
        }

        int max = Math.max(1, properties.getMaxPermits());
        long window = Math.max(1L, properties.getWindowSeconds());
        double refillPerSec = max / (double) window;
        String redisKey = KEY_PREFIX + userId;

        List<String> keys = Collections.singletonList(redisKey);
        Long allowed = stringRedisTemplate.execute(
                TOKEN_BUCKET_SCRIPT,
                keys,
                String.valueOf(max),
                String.valueOf(refillPerSec));

        if (allowed == null || allowed < 0) {
            if (allowed != null && allowed < 0) {
                log.warn("Redis 令牌桶脚本参数非法，降级放行: userId={}, maxPermits={}, windowSeconds={}", userId, max, window);
            } else {
                log.warn("Redis 限流脚本无返回，降级放行: userId={}", userId);
            }
            return joinPoint.proceed();
        }
        if (allowed == 0L) {
            log.warn("AI 封面生图触发用户维度令牌桶限流: userId={}, capacity={}, refillPerSec≈{}, windowSeconds={}",
                    userId, max, String.format("%.6f", refillPerSec), window);
            return RestResp.fail(ErrorCodeEnum.AI_IMAGE_GENERATION_BUSY);
        }

        return joinPoint.proceed();
    }
}
