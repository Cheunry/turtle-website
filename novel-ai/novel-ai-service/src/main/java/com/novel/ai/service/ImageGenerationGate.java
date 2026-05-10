package com.novel.ai.service;

import com.novel.ai.dto.resp.ImageGenJobStatusRespDto;
import com.novel.ai.dto.resp.ImageGenJobSubmitRespDto;
import com.novel.ai.image.job.ImageAsyncGenerationService;
import com.novel.common.resp.RestResp;
import org.springframework.stereotype.Service;

/**
 * 生图提交网关：HTTP 请求只负责创建任务并返回 jobId，结果由轮询接口读取。
 * <p>
 * 真正的生图任务在 {@link ImageAsyncGenerationService} 中入队执行，避免调用方线程同步等待图片生成。
 */
@Service
public class ImageGenerationGate {

    private final ImageAsyncGenerationService imageAsyncGenerationService;

    public ImageGenerationGate(ImageAsyncGenerationService imageAsyncGenerationService) {
        this.imageAsyncGenerationService = imageAsyncGenerationService;
    }

    public RestResp<ImageGenJobSubmitRespDto> generateImage(String prompt) {
        return imageAsyncGenerationService.submit(prompt);
    }

    public RestResp<ImageGenJobStatusRespDto> getJob(String jobId) {
        return imageAsyncGenerationService.getJob(jobId);
    }
}
