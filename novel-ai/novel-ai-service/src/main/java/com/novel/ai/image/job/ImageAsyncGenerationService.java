package com.novel.ai.image.job;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.novel.ai.dto.req.CoverImageAsyncSubmitReqDto;
import com.novel.ai.dto.resp.ImageGenJobStatusRespDto;
import com.novel.ai.dto.resp.ImageGenJobSubmitRespDto;
import com.novel.ai.image.exception.ImageGenerationException;
import com.novel.ai.service.ImageService;
import com.novel.common.constant.ErrorCodeEnum;
import com.novel.common.resp.RestResp;
import com.novel.user.dto.req.CoverGenerationFailedReqDto;
import com.novel.user.feign.AuthorFeign;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 异步生图：入队后立即返回 jobId；工作线程内调 {@link ImageService#generateImage(String, String)} 并维护 Redis 状态。
 * 执行失败时只通知 novel-user，由 novel-user 基于 requestId 统一做积分补偿。
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
    private final MeterRegistry meterRegistry;

    public RestResp<ImageGenJobSubmitRespDto> submit(String prompt) {
        return submit(prompt, null, null);
    }

    public RestResp<ImageGenJobSubmitRespDto> submit(CoverImageAsyncSubmitReqDto req) {
        String prompt = req.getPrompt() != null ? req.getPrompt().trim() : "";
        return submit(prompt, req.getAuthorId(), req);
    }

    private RestResp<ImageGenJobSubmitRespDto> submit(
            String rawPrompt,
            Long authorId,
            CoverImageAsyncSubmitReqDto submitRequest) {
        String prompt = rawPrompt != null ? rawPrompt.trim() : "";
        if (prompt.isEmpty()) {
            return RestResp.fail(ErrorCodeEnum.USER_REQUEST_PARAM_ERROR, "提示词不能为空");
        }

        if (isImageGenerationExecutorSaturated()) {
            log.warn("异步生图线程池已满，拒绝创建任务");
            incrementCounter("novel.ai.image.job.rejected", "reason", "saturated_precheck");
            return RestResp.fail(ErrorCodeEnum.AI_IMAGE_GENERATION_BUSY);
        }

        String jobId = UUID.randomUUID().toString().replace("-", "");
        try {
            imageJobRedisStore.createQueued(jobId, authorId, submitRequest);
        } catch (JsonProcessingException e) {
            log.error("创建异步生图任务失败", e);
            return RestResp.fail(ErrorCodeEnum.SYSTEM_ERROR, "创建任务失败");
        }

        try {
            imageGenerationExecutor.submit(() -> runJob(jobId, prompt));
            incrementCounter("novel.ai.image.job.submitted");
        } catch (RejectedExecutionException e) {
            log.warn("异步生图线程池拒绝任务 jobId={}", jobId);
            imageJobRedisStore.delete(jobId);
            incrementCounter("novel.ai.image.job.rejected", "reason", "executor_rejected");
            // 积分回滚由 novel-user 收到失败响应后统一处理，避免与 AI 侧 Feign 回滚重复
            return RestResp.fail(ErrorCodeEnum.AI_IMAGE_GENERATION_BUSY);
        }

        return RestResp.ok(new ImageGenJobSubmitRespDto(jobId));
    }

    private boolean isImageGenerationExecutorSaturated() {
        ThreadPoolExecutor executor = imageGenerationExecutor.getThreadPoolExecutor();
        return executor.getActiveCount() >= executor.getMaximumPoolSize()
                && executor.getQueue().remainingCapacity() <= 0;
    }

    private void runJob(String jobId, String prompt) {
        long startNanos = System.nanoTime();
        String status = "failure";
        String failureCategory = "unknown";
        try {
            RestResp<String> resp = imageService.generateImage(prompt, jobId);
            if (resp != null && resp.isOk() && resp.getData() != null) {
                imageJobRedisStore.markSucceeded(jobId, resp.getData());
                status = "success";
                incrementCounter("novel.ai.image.job.completed", "status", status, "failure_category", "none");
                return;
            }
            String msg = resp != null && resp.getMessage() != null ? resp.getMessage() : "生图失败";
            imageJobRedisStore.markFailed(jobId, msg);
            notifyGenerationFailed(jobId, msg);
        } catch (Exception e) {
            log.error("异步生图任务异常 jobId={}", jobId, e);
            if (e instanceof ImageGenerationException imageException) {
                failureCategory = imageException.getCategory().name().toLowerCase();
            }
            String em = userMessage(e);
            if (em.length() > 300) {
                em = em.substring(0, 300);
            }
            imageJobRedisStore.markFailed(jobId, em);
            notifyGenerationFailed(jobId, em);
        } finally {
            recordJobDuration(startNanos, status, failureCategory);
            if (!"success".equals(status)) {
                incrementCounter("novel.ai.image.job.completed",
                        "status", status,
                        "failure_category", failureCategory);
            }
        }
    }

    private static String userMessage(Exception e) {
        if (e instanceof ImageGenerationException imageException) {
            return imageException.getUserMessage();
        }
        return "封面生成失败，网站日志已经记录，后续会排查";
    }

    private void notifyGenerationFailed(String jobId, String failureReason) {
        imageJobRedisStore.readSubmitRequest(jobId).ifPresentOrElse(submitReq -> {
            CoverGenerationFailedReqDto dto = new CoverGenerationFailedReqDto();
            dto.setAuthorId(submitReq.getAuthorId());
            dto.setRequestId(submitReq.getRequestId());
            dto.setJobId(jobId);
            dto.setFailureReason(failureReason);

            RestResp<Void> r = authorFeign.notifyCoverGenerationFailed(dto);
            if (!r.isOk()) {
                log.error("异步生图失败回调未成功 jobId={}, authorId={}, requestId={}, msg={}",
                        jobId, dto.getAuthorId(), dto.getRequestId(), r.getMessage());
            } else {
                log.info("异步生图失败回调已发送 jobId={}, authorId={}, requestId={}",
                        jobId, dto.getAuthorId(), dto.getRequestId());
            }
        }, () -> log.error("异步生图失败但缺少提交上下文，无法发送失败回调 jobId={}", jobId));
    }

    public RestResp<ImageGenJobStatusRespDto> getJob(String jobId) {
        if (jobId == null || jobId.isBlank()) {
            return RestResp.fail(ErrorCodeEnum.USER_REQUEST_PARAM_ERROR, "jobId 无效");
        }
        return imageJobRedisStore.find(jobId)
                .map(RestResp::ok)
                .orElseGet(() -> RestResp.fail(ErrorCodeEnum.USER_REQUEST_PARAM_ERROR, "任务不存在或已过期"));
    }

    private void incrementCounter(String metricName, String... tags) {
        Counter.builder(metricName)
                .tags(tags)
                .register(meterRegistry)
                .increment();
    }

    private void recordJobDuration(long startNanos, String status, String failureCategory) {
        Timer.builder("novel.ai.image.job.duration")
                .description("Image generation async job duration")
                .tag("status", status)
                .tag("failure_category", failureCategory)
                .register(meterRegistry)
                .record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
    }
}
