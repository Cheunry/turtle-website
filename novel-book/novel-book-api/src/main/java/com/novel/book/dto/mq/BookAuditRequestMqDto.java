package com.novel.book.dto.mq;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 书籍审核请求 MQ 消息 DTO（业务服务 -> AI服务）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookAuditRequestMqDto {

    /**
     * 任务ID（用于关联请求和结果，保证幂等性）
     */
    private String taskId;

    /**
     * 书籍ID
     */
    private Long bookId;

    /**
     * 书籍名
     */
    private String bookName;

    /**
     * 书籍描述
     */
    private String bookDesc;

}

