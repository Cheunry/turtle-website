package com.novel.user.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 消息内容表
 */
@TableName(value = "message_content", autoResultMap = true)
@Data
@Builder
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
     * 消息正文
     */
    private String content;

    /**
     * 消息类型 (0:系统公告, 1:业务提醒, 2:私信)
     */
    private Integer type;

    /**
     * 扩展数据(JSON格式)
     * 必须在TableName注解中开启 autoResultMap = true
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extension;

    /**
     * 发送者类型 (0:系统, 1:用户, 2:作者)
     */
    private Integer senderType;

    /**
     * 发送者ID (0表示系统)
     */
    private Long senderId;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

}

