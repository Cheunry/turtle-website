package com.novel.user.dto.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

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

}
