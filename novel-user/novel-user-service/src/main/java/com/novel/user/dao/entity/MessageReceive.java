package com.novel.user.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息接收关联表
 */
@TableName("message_receive")
@Data
@Builder
public class MessageReceive {

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 关联内容表ID
     */
    private Long messageId;

    /**
     * 接收者类型 (1:用户, 2:作者)
     */
    private Integer receiverType;

    /**
     * 接收者ID
     */
    private Long receiverId;

    /**
     * 阅读状态 (0:未读, 1:已读)
     */
    private Integer isRead;

    /**
     * 阅读时间
     */
    private LocalDateTime readTime;

    /**
     * 是否删除 (0:正常, 1:已删除)
     */
    private Integer isDeleted;

}

