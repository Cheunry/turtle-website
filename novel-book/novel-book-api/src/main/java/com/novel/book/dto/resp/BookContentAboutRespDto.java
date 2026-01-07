package com.novel.book.dto.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 小说内容相关 响应DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookContentAboutRespDto {

    @Schema(description = "小说信息")
    private BookInfo bookInfo;

    @Schema(description = "章节信息")
    private ChapterInfo chapterInfo;

    @Schema(description = "章节内容")
    private String bookContent;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BookInfo {
        @Schema(description = "小说类别名")
        private String categoryName;

        @Schema(description = "作者名")
        private String authorName;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChapterInfo {
        @Schema(description = "书籍ID")
        private Long bookId;

        @Schema(description = "章节号")
        private Integer chapterNum;

        @Schema(description = "章节名")
        private String chapterName;

        @Schema(description = "章节字数")
        private Integer chapterWordCount;

        @Schema(description = "章节更新时间")
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
        private LocalDateTime chapterUpdateTime;
    }
}
