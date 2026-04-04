package com.novel.ai.dto.req;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditRuleReqDto implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 原文内容
     */
    private String contentText;

    /**
     * 审核状态 (1-通过, 2-不通过)
     */
    private Integer auditStatus;

    /**
     * 审核原因
     */
    private String auditReason;
}
