package com.novel.book.dto.mq;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 书籍信息更新 MQ 消息 DTO（异步更新书籍字数和最新章节信息）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookInfoUpdateMqDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 书籍ID
     */
    private Long bookId;

    /**
     * 章节ID（用于更新最新章节信息）
     */
    private Long chapterId;

    /**
     * 是否是新增章节
     */
    private Boolean isNew;

    /**
     * 旧章节字数（仅更新时使用）
     */
    private Integer oldChapterWordCount;

    /**
     * 新章节字数
     */
    private Integer newChapterWordCount;

    /**
     * 章节号
     */
    private Integer chapterNum;

    /**
     * 章节名
     */
    private String chapterName;

    /**
     * 章节更新时间
     */
    private LocalDateTime chapterUpdateTime;

}

