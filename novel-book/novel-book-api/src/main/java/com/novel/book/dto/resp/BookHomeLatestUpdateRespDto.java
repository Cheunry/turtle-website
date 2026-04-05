package com.novel.book.dto.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 首页「最新更新」表格行
 */
@Data
public class BookHomeLatestUpdateRespDto {

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

    @Schema(description = "最新章节更新时间")
    @JsonFormat(pattern = "MM/dd HH:mm")
    private LocalDateTime lastChapterUpdateTime;
}
