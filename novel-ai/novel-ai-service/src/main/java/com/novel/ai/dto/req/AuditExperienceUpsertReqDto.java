package com.novel.ai.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 审核经验判例写入请求 DTO（novel-ai 模块自有字段契约）。
 * <p>
 * 设计目标：novel-ai 模块不反向依赖任何业务模块（book/user/...），
 * 判例来源由外部（DB 手工拉取脚本、人审后台、在线审核管线等）将字段推给本接口，
 * ai 模块只负责"向量化 + 入库 + 召回"，不做业务判断也不拉取业务数据。
 * <p>
 * 字段语义参照 {@code content_audit} 表，但不强绑定——外部调用方可以用自己的
 * 业务主键当作 {@link #auditId}，只要保证同一个业务来源下幂等即可。
 */
@Data
@Schema(description = "审核经验判例写入请求")
public class AuditExperienceUpsertReqDto {

    /**
     * 业务主键（幂等用）。同一个 auditId 多次写入会走 ES index 的 upsert 语义，
     * 覆盖旧向量，避免重复样本。
     */
    @Schema(description = "业务主键，幂等键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long auditId;

    /**
     * 判例来源类型：{@code book} / {@code chapter} / {@code chapter_segment}。
     * 与 {@link com.novel.ai.rag.AuditExperienceMetadata#SOURCE_TYPE_BOOK} 等常量对齐。
     */
    @Schema(description = "来源类型：book/chapter/chapter_segment",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String sourceType;

    /**
     * 来源主键：书籍 ID / 章节 ID / 章节段 ID。
     */
    @Schema(description = "来源主键（书籍或章节 ID）")
    private Long sourceId;

    /**
     * 审核结果：{@code 1=通过}，{@code 2=不通过}，{@code 0=待定}。
     * 只入库"有判例价值"的记录（一般是 {@code 2}，带违规标签）。
     */
    @Schema(description = "审核结果 1 通过 2 不通过 0 待定")
    private Integer auditStatus;

    /**
     * 违规标签（暴力/色情/政治……）。本字段为空或为空串时 Indexer 会直接拒收，
     * 因为"判例"必须有标签。
     */
    @Schema(description = "违规标签，必填；空则拒收",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String violationLabel;

    /**
     * 判例规则总结。建议给大模型能直接理解的自然语言，例如
     * "同时出现血腥场景 + 未成年角色时一律判不通过"。
     */
    @Schema(description = "判例规则总结")
    private String auditRule;

    /**
     * 核心争议原文片段，作为给模型的"判例证据"。
     */
    @Schema(description = "核心争议原文片段")
    private String keySnippet;

    /**
     * 置信度（0.0~1.0）。
     */
    @Schema(description = "置信度 0.0~1.0")
    private BigDecimal confidence;

    /**
     * 经验产生时间戳（毫秒）。为空时由 Indexer 填当前时间。
     */
    @Schema(description = "经验产生时间戳，毫秒；为空则填当前时间")
    private Long createdAtMs;
}
