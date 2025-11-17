package com.novel.book.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BookSearchRespDto {

    private Long id;

    @Schema(description = "小说封面地址")
    private String picUrl;

    @Schema(description = "小说名")
    private String bookName;

}
