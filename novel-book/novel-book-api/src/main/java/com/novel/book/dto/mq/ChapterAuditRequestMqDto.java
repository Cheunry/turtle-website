package com.novel.book.dto.mq;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 章节审核请求 MQ 消息 DTO（业务服务 -> AI服务）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChapterAuditRequestMqDto {

    /**
     * 任务ID（用于关联请求和结果，保证幂等性）
     */
    private String taskId;

    /**
     * 章节ID
     */
    private Long chapterId;

    /**
     * 小说ID
     */
    private Long bookId;

    /**
     * 章节号
     */
    private Integer chapterNum;

    /**
     * 章节名
     */
    private String chapterName;

    /**
     * 章节内容
     */
    private String content;

}

