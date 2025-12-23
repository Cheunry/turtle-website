package com.novel.user.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 作者点数充值记录
 */
@Data
@TableName("author_points_recharge_log")
public class AuthorPointsRechargeLog {

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 作者ID
     */
    private Long authorId;

    /**
     * 充值金额;单位：分
     */
    private Integer rechargeAmount;

    /**
     * 充值获得的永久积分
     */
    private Integer rechargePoints;

    /**
     * 充值方式;0-支付宝 1-微信
     */
    private Integer payChannel;

    /**
     * 商户订单号
     */
    private String outTradeNo;

    /**
     * 第三方交易号（支付宝/微信交易号）
     */
    private String tradeNo;

    /**
     * 充值状态;0-待支付 1-支付成功 2-支付失败
     */
    private Integer rechargeStatus;

    /**
     * 充值完成时间
     */
    private LocalDateTime rechargeTime;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}

