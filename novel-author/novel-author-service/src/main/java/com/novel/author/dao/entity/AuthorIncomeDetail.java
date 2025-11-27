package com.novel.author.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Builder;
import lombok.Data;


import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 稿费收入明细统计
 */
@TableName("author_income_detail")
@Data
@Builder
public class AuthorIncomeDetail {


    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 作家ID
     */
    private Long authorId;

    /**
     * 小说ID;0表示全部作品
     */
    private Long bookId;

    /**
     * 收入日期
     */
    private LocalDate incomeDate;

    /**
     * 订阅总额
     */
    private Integer incomeAccount;

    /**
     * 订阅次数
     */
    private Integer incomeCount;

    /**
     * 订阅人数
     */
    private Integer incomeNumber;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
