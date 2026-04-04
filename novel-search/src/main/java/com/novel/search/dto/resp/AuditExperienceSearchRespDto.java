package com.novel.search.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class AuditExperienceSearchRespDto {

    @Schema(description = "审核状态;0-待审核 1-通过 2-不通过")
    private Integer auditStatus;

    @Schema(description = "争议/违规标签")
    private String violationLabel;

    @Schema(description = "核心争议片段")
    private String keySnippet;

    @Schema(description = "判例规则总结")
    private String auditRule;

    @Schema(description = "相似度得分")
    private Double score;
}
