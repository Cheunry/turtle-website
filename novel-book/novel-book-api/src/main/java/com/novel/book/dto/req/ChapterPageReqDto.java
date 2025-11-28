package com.novel.book.dto.req;

import com.novel.common.req.PageReqDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ChapterPageReqDto extends PageReqDto {

    /**
     * 作家ID
     */
    @Schema(description = "作家ID")
    private Long authorId;

    /**
     * 书籍ID
     */
    @Schema(description = "书籍ID")
    private Long bookId;


}
