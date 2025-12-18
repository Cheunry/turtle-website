package com.novel.book.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "章节审核结果响应DTO")
public class ChapterAuditRespDto {

    /**
     * 小说ID
     */
    @Schema(description = "小说ID")
    private Long bookId;

    /**
     * 章节NUM
     */
    @Schema(description = "章节NUM")
    private Integer chapterNum;

    /**
     * 审核状态;0-待审核 1-审核通过 2-审核不通过
     */
    @Schema(description = "审核状态;0-待审核 1-审核通过 2-审核不通过")
    private Integer auditStatus;

    /**
     * AI审核置信度
     */
    @Schema(description = "AI审核置信度")
    private BigDecimal aiConfidence;

    /**
     * 审核原因（详细）
     */
    @Schema(description = "审核原因")
    private String auditReason;
}
