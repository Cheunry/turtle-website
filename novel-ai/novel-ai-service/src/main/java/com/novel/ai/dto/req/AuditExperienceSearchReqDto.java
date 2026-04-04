package com.novel.ai.dto.req;

import lombok.Data;

@Data
public class AuditExperienceSearchReqDto {
    private String contentText;
    private Integer topK = 3;
    private Double similarityThreshold = 0.75;
}
