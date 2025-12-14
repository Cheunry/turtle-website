package com.novel.book.dto.req;

import com.novel.common.req.PageReqDto;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 小说评论分页请求 DTO
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BookCommentPageReqDto extends PageReqDto {

    /**
     * 小说ID
     */
    @Parameter(description = "小说ID")
    private Long bookId;

    /**
     * 用户ID (用于筛选)
     */
    @Parameter(description = "用户ID")
    private Long userId;

}
