package com.novel.ai.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 异步封面生图提交（内部接口）：生图完成后凭 {@link #rollbackContext} 在失败时回滚积分。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "异步封面生图提交")
public class CoverImageAsyncSubmitReqDto {

    @NotBlank
    @Schema(description = "生图提示词")
    private String prompt;

    /**
     * 与积分回滚、任务归属校验一致；由 novel-user 在扣分后填入。
     */
    @NotNull
    @Schema(description = "作者ID")
    private Long authorId;

    @NotNull
    @Schema(description = "消费类型，封面为 2")
    private Integer consumeType;

    @NotNull
    @Schema(description = "消费点数")
    private Integer consumePoints;

    @Schema(description = "关联ID（小说ID）")
    private Long relatedId;

    @Schema(description = "关联描述")
    private String relatedDesc;

    @Schema(description = "已扣免费积分（回滚用）")
    private Integer usedFreePoints;

    @Schema(description = "已扣付费积分（回滚用）")
    private Integer usedPaidPoints;
}
