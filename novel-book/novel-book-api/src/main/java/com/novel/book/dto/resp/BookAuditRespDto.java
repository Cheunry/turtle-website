package com.novel.book.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * AI审核结果响应DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookAuditRespDto {

    /**
     * 小说ID
     */
    @Schema(description = "小说ID")
    private Long id;

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