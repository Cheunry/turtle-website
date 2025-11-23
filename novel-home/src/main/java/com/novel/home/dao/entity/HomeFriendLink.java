package com.novel.home.dao.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 首页友情链接
 */
@Data
@TableName("home_friend_link")
public class HomeFriendLink {

    @TableId
    private Long id;

    /**
     * 链接名
     */
    private String linkName;

    /**
     * 链接ID
     */
    private String linkUrl;

    /**
     * 排序号
     */
    private Integer sort;

    /**
     * 是否开启;0-不开启 1-开启
     */
    private Integer isOpen;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
