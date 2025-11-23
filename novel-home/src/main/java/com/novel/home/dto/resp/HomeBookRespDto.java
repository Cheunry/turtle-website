package com.novel.home.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 首页小说展示-小说Dto
 */
@Data
public class HomeBookRespDto {

    /**
     * 推荐类型;0-轮播图 1-顶部栏 2-本周强推 3-热门推荐 4-精品推荐
     */
    @Schema(description = "推荐类型;0-轮播图 1-顶部栏 2-本周强推 3-热门推荐 4-精品推荐")
    private Integer type;


    /**
     * 小说ID
     */
    @Schema(description = "小说ID")
    private Long bookId;

    /**
     * 小说名
     */
    @Schema(description = "小说名")
    private String bookName;


    /**
     * 小说封面地址
     */
    @Schema(description = "小说封面地址")
    private String picUrl;


    /**
     * 作家名
     */
    @Schema(description = "作家名")
    private String authorName;

    /**
     * 书籍描述
     */
    @Schema(description = "书籍描述")
    private String bookDesc;

}

/*
    DTO 的存在意义：
“对外提供与内部领域模型解耦、可随意裁剪、格式化且能向前兼容的稳定数据契约。”

核心作用：
1. 隔离内部表结构，避免把实体字段直接暴露成公开 API。
2. 按需拼装/脱敏，节省带宽、防止敏感数据泄露。
3. 支持聚合、计算字段、格式化值，降低调用方复杂度。
4. 接口与领域可独立演化，实体怎么改，DTO 可保持兼容。
 */