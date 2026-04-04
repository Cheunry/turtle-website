package com.novel.book.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 内容审核表实体（书籍和章节审核）
 * 主键为 id；同一 (source_type, source_id) 可存在多条审核记录
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("content_audit")
public class ContentAudit {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 数据来源
     * 0-小说基本信息表 1-小说章节表
     */
    @TableField("source_type")
    private Integer dataSource;

    /**
     * 数据来源ID（书籍ID或章节ID）
     */
    @TableField("source_id")
    private Long dataSourceId;

    /**
     * 内容文本
     */
    @TableField("content_text")
    private String contentText;

    /**
     * AI审核置信度;范围0-1
     */
    @TableField("ai_confidence")
    private BigDecimal aiConfidence;

    /**
     * 审核状态
     * 0-待审核 1-通过 2-不通过
     */
    @TableField("audit_status")
    private Integer auditStatus;

    /**
     * 是否人工最终裁决;NULL-非人工最终裁决(或历史未标记),1-人工最终裁决
     */
    @TableField("is_human_final")
    private Integer isHumanFinal;

    /**
     * 通过/不通过原因
     */
    @TableField("audit_reason")
    private String auditReason;

    /**
     * 争议/违规标签（由AI提炼）
     */
    @TableField("violation_label")
    private String violationLabel;

    /**
     * 核心争议片段（由AI提炼）
     */
    @TableField("key_snippet")
    private String keySnippet;

    /**
     * 判例规则总结（由AI提炼）
     */
    @TableField("audit_rule")
    private String auditRule;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;
}