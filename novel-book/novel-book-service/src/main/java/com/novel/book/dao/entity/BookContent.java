package com.novel.book.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("book_content")
public class BookContent {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long chapterId;

    private String content;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
