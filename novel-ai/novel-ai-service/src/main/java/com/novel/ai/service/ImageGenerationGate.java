package com.novel.ai.service;

import com.novel.common.constant.ErrorCodeEnum;
import com.novel.common.resp.RestResp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import com.novel.ai.config.ImageGenerationExecutorProperties;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 将生图委托给有界线程池：限制并行度与排队长度；队列满时快速返回 {@link ErrorCodeEnum#AI_IMAGE_GENERATION_BUSY}。
 * <p>
 * 说明：已进入队列的请求仍会占用调用方 HTTP 线程直至 {@link Future#get} 完成；若要释放连接需改为异步任务 + 轮询。
 */
@Slf4j
@Service
public class ImageGenerationGate {

    private final ImageService imageService;
    private final ThreadPoolTaskExecutor imageGenerationExecutor;
    private final ImageGenerationExecutorProperties properties;

    public ImageGenerationGate(
            ImageService imageService,
            @Qualifier("imageGenerationExecutor") ThreadPoolTaskExecutor imageGenerationExecutor,
            ImageGenerationExecutorProperties properties) {
        this.imageService = imageService;
        this.imageGenerationExecutor = imageGenerationExecutor;
        this.properties = properties;
    }

    public RestResp<String> generateImage(String prompt) {
        Future<RestResp<String>> future;
        try {
            future = imageGenerationExecutor.submit(() -> imageService.generateImage(prompt));
        } catch (RejectedExecutionException e) {
            log.warn("生图任务被拒绝（线程池或队列已满）: {}", e.getMessage());
            return RestResp.fail(ErrorCodeEnum.AI_IMAGE_GENERATION_BUSY);
        }

        try {
            return future.get(properties.getAwaitTimeoutMs(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("生图任务等待超时，awaitTimeoutMs={}", properties.getAwaitTimeoutMs());
            return RestResp.fail(ErrorCodeEnum.SYSTEM_TIMEOUT_ERROR, "生图耗时过长，请稍后重试");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            return RestResp.fail(ErrorCodeEnum.SYSTEM_ERROR, "生图被中断");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log.error("生图任务执行失败", cause);
            String msg = cause.getMessage() != null ? cause.getMessage() : "生图失败";
            if (msg.length() > 200) {
                msg = msg.substring(0, 200);
            }
            return RestResp.fail(ErrorCodeEnum.THIRD_SERVICE_ERROR, msg);
        }
    }
}
