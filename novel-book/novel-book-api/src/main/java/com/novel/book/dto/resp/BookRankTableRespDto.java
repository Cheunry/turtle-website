package com.novel.book.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 排行榜页表格行（无封面、无简介）
 */
@Data
public class BookRankTableRespDto {

    @Schema(description = "排名，从 1 开始")
    private Integer rank;

    @Schema(description = "小说ID")
    private Long id;

    @Schema(description = "类别名")
    private String categoryName;

    @Schema(description = "书名")
    private String bookName;

    @Schema(description = "最新章节名")
    private String lastChapterName;

    @Schema(description = "作者名")
    private String authorName;

    @Schema(description = "字数")
    private Integer wordCount;
}
