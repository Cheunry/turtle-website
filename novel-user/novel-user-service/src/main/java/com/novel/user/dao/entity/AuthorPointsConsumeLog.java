package com.novel.user.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 作者点数消费记录
 */
@Data
@TableName("author_points_consume_log")
public class AuthorPointsConsumeLog {

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 作者ID
     */
    private Long authorId;

    /**
     * 消费类型;0-AI审核 1-AI润色 2-AI封面
     */
    private Integer consumeType;

    /**
     * 消费点数
     */
    private Integer consumePoints;

    /**
     * 使用的点数类型;0-免费点数 1-永久积分
     */
    private Integer pointsType;

    /**
     * 关联ID（如：章节ID、小说ID等）
     */
    private Long relatedId;

    /**
     * 关联描述（如：章节名、小说名等）
     */
    private String relatedDesc;

    /**
     * 消费日期（用于统计和查询）
     */
    private LocalDate consumeDate;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 幂等性Key
     */
    private String idempotentKey;
}

