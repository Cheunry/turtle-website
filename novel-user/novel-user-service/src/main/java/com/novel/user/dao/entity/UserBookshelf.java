package com.novel.user.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_bookshelf")
public class UserBookshelf {

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 小说ID
     */
    @TableField("book_id")
    private Long bookId;

    /**
     * 上一次阅读的章节号
     */
    @TableField("pre_chapter_num")
    private Integer preChapterNum;

    /**
     * 创建时间;
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间;
     */
    @TableField("update_time")
    private LocalDateTime updateTime;
}
