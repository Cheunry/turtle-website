package com.novel.book.dto.mq;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

/**
 * 书籍审核结果 MQ 消息 DTO（AI服务 -> 业务服务）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookAuditResultMqDto {

    /**
     * 任务ID（用于关联请求和结果，保证幂等性）
     */
    private String taskId;

    /**
     * 书籍ID
     */
    private Long bookId;

    /**
     * 审核状态;0-待审核 1-审核通过 2-审核不通过
     */
    private Integer auditStatus;

    /**
     * AI审核置信度
     */
    private BigDecimal aiConfidence;

    /**
     * 审核原因（详细）
     */
    private String auditReason;

    /**
     * 是否处理成功（用于标识AI服务处理状态）
     */
    private Boolean success;

    /**
     * 错误信息（当success=false时使用）
     */
    private String errorMessage;

}

