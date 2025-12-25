package com.novel.book.dto.mq;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 书籍更新 MQ 消息 DTO（作者提交书籍更新请求）
 * 用于将书籍更新操作完全异步化，网关只需发送此消息即可立即返回
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookUpdateMqDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 书籍ID
     */
    private Long bookId;

    /**
     * 作者ID（用于权限校验）
     */
    private Long authorId;

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
     * 类别ID
     */
    private Long categoryId;

    /**
     * 类别名
     */
    private String categoryName;

    /**
     * 作品方向;0-男频 1-女频
     */
    private Integer workDirection;

    /**
     * 是否收费;1-收费 0-免费
     */
    private Integer isVip;

    /**
     * 小说状态;0-连载中 1-已完结
     */
    private Integer bookStatus;

    /**
     * 是否开启审核
     */
    private Boolean auditEnable;

}

