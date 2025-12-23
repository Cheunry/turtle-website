package com.novel.book.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 小说点击量更新 请求DTO
 */
@Data
public class BookVisitReqDto {

    @Schema(description = "小说ID")
    private Long bookId;
}