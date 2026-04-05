package com.novel.book.dto.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 小说排行榜 响应DTO
 *
 */
@Data
public class BookRankRespDto {

    /**
     * ID
     */
    @Schema(description = "小说ID")
    private Long id;

    /**
     * 类别ID
     */
    @Schema(description = "类别ID")
    private Long categoryId;

    /**
     * 类别名
     */
    @Schema(description = "类别名")
    private String categoryName;

    /**
     * 小说封面地址
     */
    @Schema(description = "小说封面地址")
    private String picUrl;

    /**
     * 小说名
     */
    @Schema(description = "小说名")
    private String bookName;

    /**
     * 作家名
     */
    @Schema(description = "作家名")
    private String authorName;

    /**
     * 书籍描述（排行榜接口为去 HTML 后的纯文本预览，最多 40 字，超出以「…」结尾）
     */
    @Schema(description = "书籍简介预览（纯文本，最多40字）")
    private String bookDesc;

    /**
     * 总字数
     */
    @Schema(description = "总字数")
    private Integer wordCount;

    /**
     * 最新章节名
     */
    @Schema(description = "最新章节名")
    private String lastChapterName;

    /**
     * 最新章节更新时间
     */
    @Schema(description = "最新章节更新时间")
    @JsonFormat(pattern = "MM/dd HH:mm")
    private LocalDateTime lastChapterUpdateTime;

}
