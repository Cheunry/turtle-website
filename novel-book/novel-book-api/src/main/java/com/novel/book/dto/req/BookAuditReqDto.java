package com.novel.book.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BookAuditReqDto {

    /**
     * 小说ID
     */
    @Schema(description = "小说ID")
    private Long id;

    /**
     * 小说名
     */
    @Schema(description = "小说名")
    private String bookName;

    /**
     * 小说描述
     */
     @Schema(description = "小说描述")
      private String bookDesc;

}
