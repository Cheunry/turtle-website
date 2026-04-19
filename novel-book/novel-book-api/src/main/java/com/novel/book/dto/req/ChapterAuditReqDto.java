package com.novel.book.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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

    /**
     * 类别ID（书籍维度，用于 AI 按类选用审核标准）
     */
    @Schema(description = "类别ID")
    private Long categoryId;

    /**
     * 类别名称
     */
    @Schema(description = "类别名称")
    private String categoryName;

    /**
     * 作者 ID（用于学习资料绿色通道等策略）
     */
    @Schema(description = "作者ID")
    private Long authorId;
}
