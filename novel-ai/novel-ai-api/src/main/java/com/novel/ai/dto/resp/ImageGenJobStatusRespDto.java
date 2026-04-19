package com.novel.ai.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 异步生图任务状态（轮询）。
 */
@Data
@Schema(description = "异步生图任务状态")
public class ImageGenJobStatusRespDto {

    @Schema(description = "任务ID")
    private String jobId;

    @Schema(description = "所属作者ID（归属校验）")
    private Long authorId;

    /**
     * QUEUED / GENERATING / UPLOADING / SUCCEEDED / FAILED
     */
    @Schema(description = "状态枚举名")
    private String status;

    @Schema(description = "提示文案")
    private String message;

    @Schema(description = "成功时的图片 URL")
    private String imageUrl;

    @Schema(description = "失败时的简要原因")
    private String errorMessage;
}
