package com.novel.book.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentAuditRespDto {

    private Long id;

    private Integer sourceType;

    private Long sourceId;

    private String contentText;

    private BigDecimal aiConfidence;

    private Integer auditStatus;

    private Integer isHumanFinal;

    private String auditReason;

    private String violationLabel;

    private String keySnippet;

    private String auditRule;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
