package com.novel.user.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 作者积分事务
 */
@Data
@TableName("author_points_tx")
public class AuthorPointsTx {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String requestId;

    private Long authorId;

    /**
     * 1-扣减 2-回滚 3-充值
     */
    private Integer txType;

    private Integer consumeType;

    private Integer freePointsDelta;

    private Integer paidPointsDelta;

    /**
     * 1-成功 2-已回滚
     */
    private Integer status;

    private Long relatedId;

    private String relatedDesc;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
