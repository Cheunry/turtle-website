package com.novel.ai.ratelimit;

import com.novel.ai.ratelimit.config.AiTokenRateLimitProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Redis Lua token 预算桶：支持按成本预扣，以及按结算差额退还/补扣。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiTokenBucketRateLimiter {

    private static final DefaultRedisScript<Long> CONSUME_SCRIPT = new DefaultRedisScript<>();
    private static final DefaultRedisScript<Long> REFUND_SCRIPT = new DefaultRedisScript<>();

    static {
        CONSUME_SCRIPT.setResultType(Long.class);
        CONSUME_SCRIPT.setScriptText(
                """
                local capacity = tonumber(ARGV[1])
                local refill_per_sec = tonumber(ARGV[2])
                local cost = tonumber(ARGV[3])
                if capacity == nil or capacity < 1 or refill_per_sec == nil or refill_per_sec <= 0 or cost == nil or cost < 0 then
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
                if tok >= cost then
                  tok = tok - cost
                  allowed = 1
                end
                redis.call('HSET', KEYS[1], 'tok', tok, 'ts', now)
                local idle_full = capacity / refill_per_sec
                local ttl = math.ceil(math.max(120, math.min(604800, idle_full * 3)))
                redis.call('EXPIRE', KEYS[1], ttl)
                return allowed
                """);

        REFUND_SCRIPT.setResultType(Long.class);
        REFUND_SCRIPT.setScriptText(
                """
                local capacity = tonumber(ARGV[1])
                local tokens = tonumber(ARGV[2])
                if capacity == nil or capacity < 1 or tokens == nil or tokens < 0 then
                  return -1
                end
                local t = redis.call('TIME')
                local now = tonumber(t[1]) + tonumber(t[2]) / 1000000.0
                local hm = redis.call('HMGET', KEYS[1], 'tok')
                local tok = tonumber(hm[1])
                if tok == nil then
                  tok = capacity
                end
                tok = math.min(capacity, tok + tokens)
                redis.call('HSET', KEYS[1], 'tok', tok, 'ts', now)
                redis.call('EXPIRE', KEYS[1], 604800)
                return 1
                """);
    }

    private final StringRedisTemplate stringRedisTemplate;
    private final AiTokenRateLimitProperties properties;

    public ReserveResult reserve(String model, long estimatedTokens) {
        AiTokenRateLimitProperties.ModelBucketProperties bucket = properties.bucketForModel(model);
        if (!properties.isEnabled() || !bucket.isEnabled()) {
            return ReserveResult.disabled(model);
        }

        long capacity = Math.max(1L, bucket.getCapacityTokens());
        long window = Math.max(1L, bucket.getWindowSeconds());
        long cost = Math.max(1L, Math.min(estimatedTokens, capacity));
        double refillPerSec = capacity / (double) window;
        String key = redisKey(model);

        Long allowed = executeConsume(key, capacity, refillPerSec, cost);
        if (allowed == null || allowed < 0) {
            log.warn("AI token 限流脚本异常: model={}, capacity={}, windowSeconds={}, cost={}",
                    model, capacity, window, cost);
            return properties.isFailOpen()
                    ? ReserveResult.failOpen(model, cost)
                    : ReserveResult.rejected(model, cost);
        }
        return allowed == 1L
                ? ReserveResult.allowed(model, cost)
                : ReserveResult.rejected(model, cost);
    }

    public void settle(ReserveResult reservation, Long actualTokens) {
        if (reservation == null || !reservation.shouldSettle() || actualTokens == null || actualTokens < 0) {
            return;
        }
        long delta = actualTokens - reservation.reservedTokens();
        if (delta > 0) {
            reserve(reservation.model(), delta);
            return;
        }
        if (delta < 0) {
            refund(reservation.model(), -delta);
        }
    }

    public void refund(ReserveResult reservation) {
        if (reservation != null && reservation.shouldSettle()) {
            refund(reservation.model(), reservation.reservedTokens());
        }
    }

    private Long executeConsume(String key, long capacity, double refillPerSec, long cost) {
        List<String> keys = Collections.singletonList(key);
        return stringRedisTemplate.execute(
                CONSUME_SCRIPT,
                keys,
                String.valueOf(capacity),
                String.valueOf(refillPerSec),
                String.valueOf(cost));
    }

    private void refund(String model, long tokens) {
        AiTokenRateLimitProperties.ModelBucketProperties bucket = properties.bucketForModel(model);
        long capacity = Math.max(1L, bucket.getCapacityTokens());
        Long result = stringRedisTemplate.execute(
                REFUND_SCRIPT,
                Collections.singletonList(redisKey(model)),
                String.valueOf(capacity),
                String.valueOf(Math.max(1L, tokens)));
        if (result == null || result < 0) {
            log.warn("AI token 预算退还失败: model={}, tokens={}", model, tokens);
        }
    }

    private static String redisKey(String model) {
        String safeModel = model == null || model.isBlank() ? "default-text-model" : model.trim();
        return "ratelimit:{ai:tokens}:tb:model:" + safeModel;
    }

    public record ReserveResult(String model, long reservedTokens, boolean allowed, boolean failOpen, boolean disabled) {

        static ReserveResult allowed(String model, long reservedTokens) {
            return new ReserveResult(model, reservedTokens, true, false, false);
        }

        static ReserveResult rejected(String model, long reservedTokens) {
            return new ReserveResult(model, reservedTokens, false, false, false);
        }

        static ReserveResult failOpen(String model, long reservedTokens) {
            return new ReserveResult(model, reservedTokens, true, true, false);
        }

        static ReserveResult disabled(String model) {
            return new ReserveResult(model, 0L, true, false, true);
        }

        boolean shouldSettle() {
            return allowed && !failOpen && !disabled && reservedTokens > 0;
        }
    }
}
