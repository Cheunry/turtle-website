package com.novel.search.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class AuditExperienceSearchReqDto {

    @Schema(description = "待审核的文本内容")
    private String contentText;

    @Schema(description = "返回的最大结果数，默认3")
    private Integer topK = 3;

    @Schema(description = "最低相似度阈值，默认0.75")
    private Double similarityThreshold = 0.75;
}
