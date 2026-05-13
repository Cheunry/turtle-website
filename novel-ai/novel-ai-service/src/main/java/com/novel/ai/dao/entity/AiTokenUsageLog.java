package com.novel.ai.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AI 模型 token 使用流水，用于成本复盘和预估参数调优。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_token_usage_log")
public class AiTokenUsageLog {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("trace_id")
    private String traceId;

    @TableField("scene")
    private String scene;

    @TableField("model")
    private String model;

    @TableField("estimated_prompt_tokens")
    private Integer estimatedPromptTokens;

    @TableField("reserved_completion_tokens")
    private Integer reservedCompletionTokens;

    @TableField("estimated_total_tokens")
    private Integer estimatedTotalTokens;

    @TableField("actual_prompt_tokens")
    private Integer actualPromptTokens;

    @TableField("actual_completion_tokens")
    private Integer actualCompletionTokens;

    @TableField("actual_total_tokens")
    private Integer actualTotalTokens;

    @TableField("estimate_delta_tokens")
    private Integer estimateDeltaTokens;

    @TableField("status")
    private String status;

    @TableField("error_type")
    private String errorType;

    @TableField("error_message")
    private String errorMessage;

    @TableField("duration_ms")
    private Long durationMs;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
