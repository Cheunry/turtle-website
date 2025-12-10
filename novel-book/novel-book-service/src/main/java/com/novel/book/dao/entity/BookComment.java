package com.novel.book.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("book_comment")
public class BookComment {

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 评论小说ID
     */
    private Long bookId;

    /**
     * 评论用户ID
     */
    private Long userId;

    /**
     * 评价内容
     */
    private String commentContent;

    /**
     * 回复数量
     */
    private Integer replyCount;

    /**
     * 审核状态;0-待审核 1-审核通过 2-审核不通过
     */
    private Integer auditStatus;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
