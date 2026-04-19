package com.novel.ai.image.job;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.novel.ai.dto.req.CoverImageAsyncSubmitReqDto;
import com.novel.ai.dto.resp.ImageGenJobStatusRespDto;
import com.novel.ai.dto.resp.ImageGenJobSubmitRespDto;
import com.novel.ai.service.ImageService;
import com.novel.common.constant.ErrorCodeEnum;
import com.novel.common.resp.RestResp;
import com.novel.user.dto.req.AuthorPointsConsumeReqDto;
import com.novel.user.feign.AuthorFeign;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

/**
 * 异步生图：入队后立即返回 jobId；工作线程内调 {@link ImageService#generateImage(String, String)} 并维护 Redis 状态，失败时回滚积分。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageAsyncGenerationService {

    private final ImageJobRedisStore imageJobRedisStore;
    private final ImageService imageService;
    @Qualifier("imageGenerationExecutor")
    private final ThreadPoolTaskExecutor imageGenerationExecutor;
    private final AuthorFeign authorFeign;

    public RestResp<ImageGenJobSubmitRespDto> submit(CoverImageAsyncSubmitReqDto req) {
        String prompt = req.getPrompt() != null ? req.getPrompt().trim() : "";
        if (prompt.isEmpty()) {
            return RestResp.fail(ErrorCodeEnum.USER_REQUEST_PARAM_ERROR, "提示词不能为空");
        }

        String jobId = UUID.randomUUID().toString().replace("-", "");
        try {
            imageJobRedisStore.createQueued(jobId, req.getAuthorId(), req);
        } catch (JsonProcessingException e) {
            log.error("创建异步生图任务失败", e);
            return RestResp.fail(ErrorCodeEnum.SYSTEM_ERROR, "创建任务失败");
        }

        try {
            imageGenerationExecutor.submit(() -> runJob(jobId, prompt));
        } catch (RejectedExecutionException e) {
            log.warn("异步生图线程池拒绝任务 jobId={}", jobId);
            imageJobRedisStore.markFailed(jobId, "当前生图任务较多，请稍后再试");
            // 积分回滚由 novel-user 收到失败响应后统一处理，避免与 AI 侧 Feign 回滚重复
            return RestResp.fail(ErrorCodeEnum.AI_IMAGE_GENERATION_BUSY);
        }

        return RestResp.ok(new ImageGenJobSubmitRespDto(jobId));
    }

    private void runJob(String jobId, String prompt) {
        try {
            RestResp<String> resp = imageService.generateImage(prompt, jobId);
            if (resp != null && resp.isOk() && resp.getData() != null) {
                imageJobRedisStore.markSucceeded(jobId, resp.getData());
                return;
            }
            String msg = resp != null && resp.getMessage() != null ? resp.getMessage() : "生图失败";
            imageJobRedisStore.markFailed(jobId, msg);
            tryRollback(jobId);
        } catch (Exception e) {
            log.error("异步生图任务异常 jobId={}", jobId, e);
            String em = e.getMessage() != null ? e.getMessage() : "生图异常";
            if (em.length() > 300) {
                em = em.substring(0, 300);
            }
            imageJobRedisStore.markFailed(jobId, em);
            tryRollback(jobId);
        }
    }

    private void tryRollback(String jobId) {
        imageJobRedisStore.readRollback(jobId).ifPresent(rb -> {
            AuthorPointsConsumeReqDto dto = toRollbackDto(rb);
            RestResp<Void> r = authorFeign.rollbackPoints(dto);
            if (!r.isOk()) {
                log.error("异步生图失败后积分回滚未成功 jobId={}, msg={}", jobId, r.getMessage());
            } else {
                log.info("异步生图失败后积分已回滚 jobId={}, authorId={}", jobId, dto.getAuthorId());
            }
        });
    }

    private static AuthorPointsConsumeReqDto toRollbackDto(CoverImageAsyncSubmitReqDto s) {
        return AuthorPointsConsumeReqDto.builder()
                .authorId(s.getAuthorId())
                .consumeType(s.getConsumeType())
                .consumePoints(s.getConsumePoints())
                .relatedId(s.getRelatedId())
                .relatedDesc(s.getRelatedDesc())
                .usedFreePoints(s.getUsedFreePoints())
                .usedPaidPoints(s.getUsedPaidPoints())
                .build();
    }

    public RestResp<ImageGenJobStatusRespDto> getJob(String jobId) {
        if (jobId == null || jobId.isBlank()) {
            return RestResp.fail(ErrorCodeEnum.USER_REQUEST_PARAM_ERROR, "jobId 无效");
        }
        return imageJobRedisStore.find(jobId)
                .map(RestResp::ok)
                .orElseGet(() -> RestResp.fail(ErrorCodeEnum.USER_REQUEST_PARAM_ERROR, "任务不存在或已过期"));
    }
}
