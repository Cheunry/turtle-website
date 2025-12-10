package com.novel.book.dto.req;

import lombok.Data;

@Data
public class ChapterDelReqDto {

    /**
     * 章节ID
     */
//    private Long chapterId;

    /**
     * 章节号
     */
    private Integer chapterNum;

    /**
     * 小说ID
     */
    private Long bookId;

    /**
     * 作者ID
     */
    private Long authorId;
}
