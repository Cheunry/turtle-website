package com.novel.book.dto.mq;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 书籍提交 MQ 消息 DTO（包含新增和更新）
 * 用于将书籍新增/更新操作完全异步化，网关只需发送此消息即可立即返回
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookSubmitMqDto {

    /**
     * 操作类型：ADD-新增，UPDATE-更新
     */
    private String operationType;

    /**
     * 书籍ID（更新时必填）
     */
    private Long bookId;

    /**
     * 作家ID
     */
    private Long authorId;

    /**
     * 作家笔名（新增时使用）
     */
    private String penName;

    /**
     * 作品方向;0-男频 1-女频
     */
    private Integer workDirection;

    /**
     * 类别ID
     */
    private Long categoryId;

    /**
     * 类别名
     */
    private String categoryName;

    /**
     * 小说封面地址
     */
    private String picUrl;

    /**
     * 小说名
     */
    private String bookName;

    /**
     * 书籍描述
     */
    private String bookDesc;

    /**
     * 是否收费;1-收费 0-免费
     */
    private Integer isVip;

    /**
     * 书籍状态;0-连载中 1-已完结
     */
    private Integer bookStatus;

    /**
     * 是否开启审核
     */
    private Boolean auditEnable;

}

