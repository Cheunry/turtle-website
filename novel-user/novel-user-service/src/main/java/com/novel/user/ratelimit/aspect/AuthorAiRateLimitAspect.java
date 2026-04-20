package com.novel.user.ratelimit.aspect;

import com.novel.common.auth.UserHolder;
import com.novel.common.constant.ErrorCodeEnum;
import com.novel.common.resp.RestResp;
import com.novel.user.ratelimit.AuthorAiRateLimitScene;
import com.novel.user.ratelimit.annotation.AuthorAiRateLimit;
import com.novel.user.ratelimit.config.AuthorAiRateLimitProperties;
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
 * 作家端 AI 接口：Redis 令牌桶限流（Hash + Lua，{@code TIME} 驱动补充）。各场景阈值见 {@link AuthorAiRateLimitProperties}。
 */
@Slf4j
@Aspect
@Component
@Order(10)
@RequiredArgsConstructor
public class AuthorAiRateLimitAspect {

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
    private final AuthorAiRateLimitProperties properties;

    @Around("@annotation(com.novel.user.ratelimit.annotation.AuthorAiRateLimit)")
    public Object aroundAuthorAi(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        AuthorAiRateLimit ann = method.getAnnotation(AuthorAiRateLimit.class);
        if (ann == null) {
            return joinPoint.proceed();
        }
        AuthorAiRateLimitScene scene = ann.value();
        AuthorAiRateLimitProperties.SceneProperties sceneProps = properties.forScene(scene);
        if (!sceneProps.isEnabled()) {
            return joinPoint.proceed();
        }
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            return joinPoint.proceed();
        }

        int max = Math.max(1, sceneProps.getMaxPermits());
        long window = Math.max(1L, sceneProps.getWindowSeconds());
        double refillPerSec = max / (double) window;
        String redisKey = redisKeyPrefix(scene) + userId;

        List<String> keys = Collections.singletonList(redisKey);
        Long allowed =
                stringRedisTemplate.execute(
                        TOKEN_BUCKET_SCRIPT, keys, String.valueOf(max), String.valueOf(refillPerSec));

        if (allowed == null || allowed < 0) {
            if (allowed != null && allowed < 0) {
                log.warn(
                        "Redis 令牌桶脚本参数非法，降级放行: scene={}, userId={}, maxPermits={}, windowSeconds={}",
                        scene,
                        userId,
                        max,
                        window);
            } else {
                log.warn("Redis 限流脚本无返回，降级放行: scene={}, userId={}", scene, userId);
            }
            return joinPoint.proceed();
        }
        if (allowed == 0L) {
            log.warn(
                    "作家端 AI 接口触发用户维度令牌桶限流: scene={}, userId={}, capacity={}, refillPerSec≈{}, windowSeconds={}",
                    scene,
                    userId,
                    max,
                    String.format("%.6f", refillPerSec),
                    window);
            if (SseEmitter.class.isAssignableFrom(method.getReturnType())) {
                return rateLimitedSseEmitter(scene);
            }
            return RestResp.fail(errorCode(scene));
        }

        return joinPoint.proceed();
    }

    private static SseEmitter rateLimitedSseEmitter(AuthorAiRateLimitScene scene) {
        SseEmitter emitter = new SseEmitter(5_000L);
        ErrorCodeEnum err = errorCode(scene);
        String safeMsg =
                err.getMessage() == null
                        ? ""
                        : err.getMessage()
                                .replace("\\", "\\\\")
                                .replace("\"", "\\\"")
                                .replace("\n", " ");
        String json = "{\"code\":\"" + err.getCode() + "\",\"message\":\"" + safeMsg + "\"}";
        try {
            emitter.send(SseEmitter.event().name("error").data(json));
        } catch (IOException e) {
            log.debug("限流 SSE error 事件发送失败", e);
        }
        emitter.complete();
        return emitter;
    }

    /**
     * 与历史封面桶 key 保持一致，避免 WRONGTYPE / 状态迁移问题。
     */
    private static String redisKeyPrefix(AuthorAiRateLimitScene scene) {
        return switch (scene) {
            case COVER_IMAGE -> "ratelimit:{author:ai:cover}:tb:user:";
            case AUDIT -> "ratelimit:{author:ai:audit}:tb:user:";
            case POLISH -> "ratelimit:{author:ai:polish}:tb:user:";
        };
    }

    private static ErrorCodeEnum errorCode(AuthorAiRateLimitScene scene) {
        return scene == AuthorAiRateLimitScene.COVER_IMAGE
                ? ErrorCodeEnum.AI_IMAGE_GENERATION_BUSY
                : ErrorCodeEnum.AI_AUTHOR_RATE_LIMIT;
    }
}
