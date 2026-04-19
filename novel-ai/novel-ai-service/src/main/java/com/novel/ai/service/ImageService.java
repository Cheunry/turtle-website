package com.novel.ai.service;

import com.novel.common.resp.RestResp;

public interface ImageService {

    /**
     * 根据提示词生成图片
     * @param prompt 提示词
     * @return 图片URL
     */
    RestResp<String> generateImage(String prompt);

    /**
     * 根据提示词生成图片（异步任务时可传 jobId 以更新 Redis 进度）。
     */
    RestResp<String> generateImage(String prompt, String jobId);

}
