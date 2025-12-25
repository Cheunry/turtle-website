package com.novel.ai.service.impl;

import com.alibaba.cloud.ai.dashscope.image.DashScopeImageOptions;
import com.novel.ai.service.ImageService;
import com.novel.common.resp.RestResp;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.toolkit.trace.ActiveSpan;
import org.apache.skywalking.apm.toolkit.trace.Trace;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ImageServiceImpl implements ImageService {

    @Resource
    private ImageModel imageModel;

    /**
     * 生成图片（带 SkyWalking 监控）
     * 增加重试机制：当遇到异常（如超时、Pending、限流）时自动重试
     * 策略优化：采用指数退避（Exponential Backoff）以符合 DashScope 的轮询建议
     * maxAttempts = 15: 覆盖约 1-2 分钟的等待时间
     * backoff: 初始间隔 3000ms (3秒)，倍率 1.5，最大间隔 10000ms (10秒)
     * 参考链接：https://bailian.console.aliyun.com/?spm=5176.30510405.J_bQ9d6wtWdX1_RtKN0y7Ar.1.d05159deC7bd3D&tab=doc#/doc/?type=model&url=2848513
     */
    @Override
    @Trace(operationName = "AI生成图片")
    @Retryable(retryFor = {Exception.class}, maxAttempts = 15,
            backoff = @Backoff(delay = 3000, multiplier = 1.5, maxDelay = 10000))
    public RestResp<String> generateImage(String prompt) {
        // 设置 SkyWalking 监控标签
        ActiveSpan.tag("ai.model", "qwen-image-plus");
        ActiveSpan.tag("ai.operation", "generate_image");
        ActiveSpan.tag("image.size", "1140x1472");
        if (prompt != null) {
            ActiveSpan.tag("prompt.length", String.valueOf(prompt.length()));
            // 记录 prompt 前50字（避免记录过长）
            String promptPreview = prompt.length() > 50 ? prompt.substring(0, 50) : prompt;
            ActiveSpan.tag("prompt.preview", promptPreview);
        }
        
        long startTime = System.currentTimeMillis();
        
        try {
            log.info("开始调用AI生图，prompt前50字: {}", prompt);
            
            // 构建生图请求
            /*
 通义千问 Qwen-Image：仅支持以下 5 种固定的分辨率：
1328*1328（默认值）：1:1。
1664*928: 16:9。
928*1664: 9:16。
1472*1140: 4:3。
1140*1472: 3:4。
通义万相 V2 版模型 (2.0 及以上版本)：支持在 [512, 1440] 像素范围内任意组合宽高，
总像素不超过 1440*1440。常用分辨率：
1024*1024（默认值）：1:1。
1440*810: 16:9。
810*1440: 9:16。
1440*1080: 4:3。
1080*1440: 3:4。
             */
            ImagePrompt imagePrompt = new ImagePrompt(prompt,
                    DashScopeImageOptions.builder()
                            .withWidth(1140)
                            .withHeight(1472)
                            .withN(1) // 生成1张
                            .build());

            // 调用模型
            ImageResponse response = imageModel.call(imagePrompt);

            // 获取结果
            // DashScope 返回的结果需要检查 null
            if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
                long duration = System.currentTimeMillis() - startTime;
                ActiveSpan.tag("ai.duration.ms", String.valueOf(duration));
                ActiveSpan.tag("ai.status", "pending_or_failed");
                ActiveSpan.tag("ai.error", "response_is_null");
                log.warn("AI生图返回结果为空，可能正在生成中或失败，尝试抛出异常以触发重试...");
                // 抛出异常，触发 @Retryable 重试
                throw new RuntimeException("AI生图结果为空");
            }
            
            String url = response.getResult().getOutput().getUrl();
            long duration = System.currentTimeMillis() - startTime;
            
            ActiveSpan.tag("ai.duration.ms", String.valueOf(duration));
            ActiveSpan.tag("ai.status", "success");
            ActiveSpan.tag("ai.image.url", url != null ? "generated" : "null");
            
            log.info("图片生成成功，耗时: {}ms, URL: {}", duration, url);
            return RestResp.ok(url);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            ActiveSpan.tag("ai.duration.ms", String.valueOf(duration));
            ActiveSpan.tag("ai.status", "error");
            ActiveSpan.tag("ai.error.type", e.getClass().getSimpleName());
            String errorMsg = e.getMessage();
            if (errorMsg != null && errorMsg.length() > 200) {
                errorMsg = errorMsg.substring(0, 200);
            }
            ActiveSpan.tag("ai.error.message", errorMsg != null ? errorMsg : "unknown");
            
            // 记录异常（重试时会多次记录，这是正常的）
            ActiveSpan.error(e);
            
            log.warn("AI生图调用异常，耗时: {}ms, 准备重试 (如果未达到最大重试次数). 异常信息: {}", duration, e.getMessage());
            // 继续抛出异常，让 @Retryable 捕获并重试
            throw e; 
        }
    }

}
