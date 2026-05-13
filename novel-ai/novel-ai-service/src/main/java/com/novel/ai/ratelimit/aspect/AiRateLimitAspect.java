package com.novel.ai.ratelimit.aspect;

import com.novel.ai.ratelimit.AiRateLimitScene;
import com.novel.ai.ratelimit.annotation.AiRateLimit;
import com.novel.ai.ratelimit.config.AiRateLimitProperties;
import com.novel.common.constant.ErrorCodeEnum;
import com.novel.common.resp.RestResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

/**
 * AI 服务内部全局 Redis 令牌桶限流（按场景保护模型与生图资源）。
 */
@Slf4j
@Aspect
@Component
@Order(10)
@RequiredArgsConstructor
public class AiRateLimitAspect {

    private static final DefaultRedisScript<Long> TOKEN_BUCKET_SCRIPT = new DefaultRedisScript<>();

    static {
        TOKEN_BUCKET_SCRIPT.setResultType(Long.class);
        TOKEN_BUCKET_SCRIPT.setScriptText(
                """
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
    private final AiRateLimitProperties properties;

    @Around("@annotation(com.novel.ai.ratelimit.annotation.AiRateLimit)")
    public Object aroundAiResource(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        AiRateLimit ann = method.getAnnotation(AiRateLimit.class);
        if (ann == null) {
            return joinPoint.proceed();
        }

        AiRateLimitScene scene = ann.value();
        AiRateLimitProperties.SceneProperties sceneProps = properties.forScene(scene);
        if (!sceneProps.isEnabled()) {
            return joinPoint.proceed();
        }

        int max = Math.max(1, sceneProps.getMaxPermits());
        long window = Math.max(1L, sceneProps.getWindowSeconds());
        double refillPerSec = max / (double) window;
        String redisKey = redisKey(scene);

        List<String> keys = Collections.singletonList(redisKey);
        Long allowed = stringRedisTemplate.execute(
                TOKEN_BUCKET_SCRIPT, keys, String.valueOf(max), String.valueOf(refillPerSec));

        if (allowed == null || allowed < 0) {
            log.warn("AI 服务限流脚本异常，降级放行: scene={}, maxPermits={}, windowSeconds={}",
                    scene, max, window);
            return joinPoint.proceed();
        }

        if (allowed == 0L) {
            log.warn("AI 服务触发全局令牌桶限流: scene={}, capacity={}, refillPerSec≈{}, windowSeconds={}",
                    scene, max, String.format("%.6f", refillPerSec), window);
            if (SseEmitter.class.isAssignableFrom(method.getReturnType())) {
                return rateLimitedSseEmitter();
            }
            return RestResp.fail(ErrorCodeEnum.AI_SERVICE_RATE_LIMIT);
        }

        return joinPoint.proceed();
    }

    private static String redisKey(AiRateLimitScene scene) {
        return "ratelimit:{ai:global}:tb:scene:" + scene.name().toLowerCase();
    }

    private static SseEmitter rateLimitedSseEmitter() {
        SseEmitter emitter = new SseEmitter(5_000L);
        ErrorCodeEnum err = ErrorCodeEnum.AI_SERVICE_RATE_LIMIT;
        String safeMsg = err.getMessage() == null
                ? ""
                : err.getMessage()
                        .replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\n", " ");
        String json = "{\"code\":\"" + err.getCode() + "\",\"message\":\"" + safeMsg + "\"}";
        try {
            emitter.send(SseEmitter.event().name("error").data(json));
        } catch (IOException e) {
            log.debug("AI 服务限流 SSE error 事件发送失败", e);
        }
        emitter.complete();
        return emitter;
    }
}
