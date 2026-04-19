package com.novel.ai.image.job;

/**
 * 异步生图任务状态。
 */
public enum ImageGenJobStatus {
    QUEUED,
    GENERATING,
    UPLOADING,
    SUCCEEDED,
    FAILED
}
