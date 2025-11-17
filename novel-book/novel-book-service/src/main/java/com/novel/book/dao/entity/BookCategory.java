package com.novel.book.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("book_category")
public class BookCategory  {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Integer workDirection;

    private String name;

    private Integer sort;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
