package com.novel.user.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息接收关联实体
 */
@Data
@TableName("message_receive")
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
     * 接收者ID (对应UserID或AuthorID)
     */
    private Long receiverId;

    /**
     * 接收者身份类型 (0:普通用户/UserID, 1:作者/AuthorID)
     */
    private Integer receiverType;

    /**
     * 阅读状态 (0:未读, 1:已读)
     */
    private Integer isRead;

    /**
     * 阅读时间
     */
    private LocalDateTime readTime;

    /**
     * 是否逻辑删除 (0:正常, 1:用户已删)
     */
    private Integer isDeleted;

}
