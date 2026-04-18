package com.novel.ai.mq.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 人审工单消息体。由 Agent 的 {@code escalateToHuman} Tool 在以下场景产生：
 * <ol>
 *     <li>AI 模型置信度过低（如 {@code confidence < 0.6}）；</li>
 *     <li>命中高严重度政策但模型判 pending；</li>
 *     <li>模型主动认为需要人工介入（通过 tool 调用表达）。</li>
 * </ol>
 * <p>
 * <b>序列化策略</b>：{@link JsonInclude.Include#NON_NULL}，避免冗余字段污染 MQ；
 * 同时使用 {@link Builder} 保证字段只在构造期赋值，消费方拿到的是不可变快照。
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "人审工单 MQ 消息体")
public class HumanReviewTaskMqDto {

    @Schema(description = "工单唯一 ID，幂等键；调用方填写或由 Producer 兜底生成")
    private String taskId;

    @Schema(description = "来源类型：book / chapter / agent_tool", requiredMode = Schema.RequiredMode.REQUIRED)
    private String sourceType;

    @Schema(description = "来源主键（书籍或章节 ID）")
    private Long sourceId;

    @Schema(description = "从属书籍 ID（章节场景下便于人审按书归类）")
    private Long bookId;

    @Schema(description = "升级原因摘要，人审列表页展示", requiredMode = Schema.RequiredMode.REQUIRED)
    private String reason;

    @Schema(description = "疑似违规的原文片段（可截断，<=300 字）")
    private String keySnippet;

    @Schema(description = "AI 初判状态：0 待定 / 1 通过 / 2 不通过")
    private Integer aiAuditStatus;

    @Schema(description = "AI 置信度 0.0~1.0")
    private BigDecimal aiConfidence;

    @Schema(description = "建议处置：reject / pending / pass-with-warning")
    private String suggestedAction;

    @Schema(description = "工单创建时间戳（毫秒）")
    private Long createdAtMs;
}
