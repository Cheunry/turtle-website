package com.novel.ai.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 审核经验判例批量写入结果。字段含义与 {@code AuditExperienceIndexer.IndexResult} 对齐，
 * 扁平化后直接给 inner endpoint 的调用方观察。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "审核经验判例批量写入结果")
public class AuditExperienceUpsertRespDto {

    @Schema(description = "请求总条数")
    private int totalScanned;

    @Schema(description = "实际写入向量库的条数")
    private int accepted;

    @Schema(description = "校验不通过被跳过的条数（如违规标签为空）")
    private int skipped;

    @Schema(description = "写入向量库失败的条数")
    private int failed;

    @Schema(description = "是否 dryRun（只校验不写入）")
    private boolean dryRun;
}
