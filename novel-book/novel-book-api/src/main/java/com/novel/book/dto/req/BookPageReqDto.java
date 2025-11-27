package com.novel.book.dto.req;

import com.novel.common.req.PageReqDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)              // 将超类的字段也纳入比较范围
public class BookPageReqDto extends PageReqDto {

    /**
     * 作家ID
     */
    @Schema(description = "作家ID")
    private Long authorId;


}