package com.novel.user.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 用户书架 响应DTO
 */
@Data
@Builder
public class UserBookshelfRespDto {

    @Schema(description = "小说ID")
    private Long bookId;

    @Schema(description = "小说名")
    private String bookName;

    @Schema(description = "小说封面地址")
    private String picUrl;

    @Schema(description = "作者名")
    private String authorName;

    @Schema(description = "首章节号")
    private Integer firstChapterNum;

    @Schema(description = "上次阅读的章节号")
    private Integer preChapterNum;

    @Schema(description = "审核状态;0-待审核 1-审核通过 2-审核不通过")
    private Integer auditStatus;

}
