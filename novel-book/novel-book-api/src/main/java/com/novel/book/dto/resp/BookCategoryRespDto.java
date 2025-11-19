package com.novel.book.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 小说分类 响应DTO

 */
@Data
@Builder
public class BookCategoryRespDto {

    /**
     * 类别ID
     */
    @Schema(description = "类别ID")
    private Long id;

    /**
     * 类别名
     */
    @Schema(description = "类别名")
    private String name;

}
