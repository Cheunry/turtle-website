package com.novel.user.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息内容实体
 */
@Data
@TableName("message_content")
public class MessageContent {

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 消息标题
     */
    private String title;

    /**
     * 消息正文(支持HTML)
     */
    private String content;

    /**
     * 消息类型 (0:系统公告/全员, 1:订阅更新/追更, 2:作家助手/审核, 3:私信)
     */
    private Integer type;

    /**
     * 跳转链接(如: /author/appeal?id=123)
     */
    private String link;

    /**
     * 业务ID(关联的书籍ID、章节ID等)
     */
    private Long busId;

    /**
     * 业务类型(如: book, chapter, author)
     */
    private String busType;

    /**
     * 扩展数据(JSON)
     */
    private String extension;

    /**
     * 消息/链接过期时间(NULL表示永不过期)
     */
    private LocalDateTime expireTime;

    /**
     * 发送者类型 (0:系统, 1:用户)
     */
    private Integer senderType;

    /**
     * 发送者ID
     */
    private Long senderId;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

}
