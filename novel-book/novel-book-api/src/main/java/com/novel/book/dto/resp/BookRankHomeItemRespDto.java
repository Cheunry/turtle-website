package com.novel.book.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 首页侧栏榜单行：每行排名+书名；仅第一名带封面与简介预览
 */
@Data
public class BookRankHomeItemRespDto {

    @Schema(description = "排名，从 1 开始")
    private Integer rank;

    @Schema(description = "小说ID")
    private Long id;

    @Schema(description = "书名")
    private String bookName;

    @Schema(description = "封面，仅第一名为非空")
    private String picUrl;

    @Schema(description = "简介预览（纯文本最多40字），仅第一名为非空")
    private String bookDesc;
}
