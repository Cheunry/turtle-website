package com.novel.ai.image.job;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.ai.dto.req.CoverImageAsyncSubmitReqDto;
import com.novel.ai.dto.resp.ImageGenJobStatusRespDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 异步生图任务：Redis Hash + TTL。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImageJobRedisStore {

    static final String KEY_PREFIX = "novel:ai:image:job:";
    private static final Duration DEFAULT_TTL = Duration.ofHours(24);

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public void createQueued(String jobId, Long authorId, CoverImageAsyncSubmitReqDto rollback)
            throws JsonProcessingException {
        String key = KEY_PREFIX + jobId;
        Map<String, String> map = new HashMap<>();
        map.put("status", ImageGenJobStatus.QUEUED.name());
        map.put("message", "排队中");
        map.put("authorId", String.valueOf(authorId));
        map.put("rollbackJson", objectMapper.writeValueAsString(rollback));
        stringRedisTemplate.opsForHash().putAll(key, map);
        stringRedisTemplate.expire(key, DEFAULT_TTL);
    }

    public void update(String jobId, ImageGenJobStatus status, String message) {
        String key = KEY_PREFIX + jobId;
        stringRedisTemplate.opsForHash().put(key, "status", status.name());
        if (message != null) {
            stringRedisTemplate.opsForHash().put(key, "message", message);
        }
        stringRedisTemplate.expire(key, DEFAULT_TTL);
    }

    public void markSucceeded(String jobId, String imageUrl) {
        String key = KEY_PREFIX + jobId;
        stringRedisTemplate.opsForHash().put(key, "status", ImageGenJobStatus.SUCCEEDED.name());
        stringRedisTemplate.opsForHash().put(key, "message", "完成");
        stringRedisTemplate.opsForHash().put(key, "imageUrl", imageUrl != null ? imageUrl : "");
        stringRedisTemplate.expire(key, DEFAULT_TTL);
    }

    public void markFailed(String jobId, String errorMessage) {
        String key = KEY_PREFIX + jobId;
        stringRedisTemplate.opsForHash().put(key, "status", ImageGenJobStatus.FAILED.name());
        stringRedisTemplate.opsForHash().put(key, "message", "失败");
        stringRedisTemplate.opsForHash().put(key, "errorMessage", errorMessage != null ? errorMessage : "");
        stringRedisTemplate.expire(key, DEFAULT_TTL);
    }

    public Optional<ImageGenJobStatusRespDto> find(String jobId) {
        String key = KEY_PREFIX + jobId;
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(key);
        if (entries == null || entries.isEmpty()) {
            return Optional.empty();
        }
        ImageGenJobStatusRespDto dto = new ImageGenJobStatusRespDto();
        dto.setJobId(jobId);
        dto.setStatus(str(entries.get("status")));
        dto.setMessage(str(entries.get("message")));
        dto.setImageUrl(str(entries.get("imageUrl")));
        dto.setErrorMessage(str(entries.get("errorMessage")));
        String aid = str(entries.get("authorId"));
        if (aid != null && !aid.isEmpty()) {
            try {
                dto.setAuthorId(Long.parseLong(aid));
            } catch (NumberFormatException ignored) {
                dto.setAuthorId(null);
            }
        }
        return Optional.of(dto);
    }

    public Optional<CoverImageAsyncSubmitReqDto> readRollback(String jobId) {
        String key = KEY_PREFIX + jobId;
        Object raw = stringRedisTemplate.opsForHash().get(key, "rollbackJson");
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(raw.toString(), CoverImageAsyncSubmitReqDto.class));
        } catch (JsonProcessingException e) {
            log.warn("解析 rollbackJson 失败 jobId={}", jobId, e);
            return Optional.empty();
        }
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }
}
