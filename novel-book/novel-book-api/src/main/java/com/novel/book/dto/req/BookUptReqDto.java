package com.novel.book.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;


/**
 * 小说更新请求 DTO
 */
@Data
public class BookUptReqDto {

    /**
     * 小说ID
     */
    @Schema(description = "小说ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    private Long bookId;

    /**
     * 小说封面地址
     */
    @Schema(description = "小说封面地址")
    private String picUrl;

    /**
     * 小说名
     */
    @Schema(description = "小说名")
    private String bookName;

    /**
     * 书籍描述
     */
    @Schema(description = "书籍描述")
    private String bookDesc;

    /**
     * 类别ID
     */
    @Schema(description = "类别ID")
    private Long categoryId;

    /**
     * 类别名
     */
    @Schema(description = "类别名")
    private String categoryName;

    /**
     * 作品方向;0-男频 1-女频
     */
    @Schema(description = "作品方向;0-男频 1-女频")
    private Integer workDirection;

    /**
     * 是否收费;1-收费 0-免费
     */
    @Schema(description = "是否收费;1-收费 0-免费")
    private Integer isVip;
    
    /**
     * 作家ID (用于权限校验)
     */
    private Long authorId;

    /**
     * 小说状态;0-连载中 1-已完结
     */
    @Schema(description = "小说状态;0-连载中 1-已完结")
    private Integer bookStatus;

}
