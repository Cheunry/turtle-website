package com.novel.user.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * AI 封面生图失败回调：novel-ai 只上报失败事实，积分补偿由 novel-user 统一处理。
 */
@Data
@Schema(description = "AI封面生图失败回调")
public class CoverGenerationFailedReqDto {

    @NotNull
    @Schema(description = "作者ID")
    private Long authorId;

    @NotBlank
    @Schema(description = "原积分扣减业务幂等号")
    private String requestId;

    @NotBlank
    @Schema(description = "生图任务ID")
    private String jobId;

    @Schema(description = "失败原因")
    private String failureReason;
}
