package com.novel.book.dto.mq;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 书籍章节更新 MQ 消息 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookChapterUpdateDto {

    /**
     * 小说ID
     */
    private Long bookId;

    /**
     * 小说名
     */
    private String bookName;

    /**
     * 作者ID
     */
    private Long authorId;

    /**
     * 作者名
     */
    private String authorName;

    /**
     * 章节ID
     */
    private Long chapterId;

    /**
     * 章节名
     */
    private String chapterName;

    /**
     * 章节号
     */
    private Integer chapterNum;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

}

