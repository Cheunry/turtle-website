package com.novel.book.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "章节审核请求DTO")
public class ChapterAuditReqDto {
    /**
     * 小说ID
     */
    @Schema(description = "小说ID")
    private Long bookId;
    /**
     * 章节NUM
     */
    @Schema(description = "章节NUM")
    private Integer chapterNum;
    /**
     * 章节名
     */
    @Schema(description = "章节名")
    private String chapterName;

    /**
     * 章节内容
     */
    @Schema(description = "章节内容")
    private String content;
}
