package com.novel.ai.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditRuleRespDto implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 争议/违规标签
     */
    private String violationLabel;

    /**
     * 核心争议片段
     */
    private String keySnippet;

    /**
     * 判例规则总结
     */
    private String auditRule;
}
