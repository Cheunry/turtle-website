package com.novel.book.dto.req;

import com.novel.common.req.PageReqDto;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class CommentPageReqDto extends PageReqDto {

    /**
     * 评论ID
     */
    private Long id;

}
