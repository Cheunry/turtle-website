package com.novel.ai.dto.resp;

import lombok.Data;

@Data
public class AuditExperienceSearchRespDto {
    private Integer auditStatus;
    private String violationLabel;
    private String keySnippet;
    private String auditRule;
    private Double score;
}
