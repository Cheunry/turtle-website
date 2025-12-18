package com.novel.book.dao.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 内容审核表实体（书籍和章节审核）
 * 注意：主键是 (source_type, source_id) 联合主键，id 只是普通字段
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("content_audit")
public class ContentAudit {

    /**
     * 顺序ID（普通字段，不是主键）
     */
    @TableField("id")
    private Long id;

    /**
     * 数据来源（联合主键的一部分）
     * 0-小说基本信息表 1-小说章节表
     */
    @TableField("source_type")
    private Integer dataSource;

    /**
     * 数据来源ID（联合主键的一部分）
     * 书籍ID或章节ID
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
     * 通过/不通过原因
     */
    @TableField("audit_reason")
    private String auditReason;

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