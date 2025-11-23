package com.novel.home.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 首页友情链接-响应Dto
 */
@Data
public class HomeFriendLinkRespDto{

    /**
     * 链接名
     */
    @Schema(description = "链接名")
    private String linkName;

    /**
     * 链接ID
     */
    @Schema(description = "链接ID")
    private String linkUrl;
}
