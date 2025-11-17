package com.novel.book.dto.req;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BookSearchReqDto {
    /**
     * 前端发来的字符串
     */
    private String InputString;

}
