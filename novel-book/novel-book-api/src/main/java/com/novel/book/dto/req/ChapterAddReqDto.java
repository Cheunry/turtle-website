package com.novel.book.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.validator.constraints.Length;


@Data
public class ChapterAddReqDto {

    /**
     * 小说ID
     */
    private Long bookId;

    /**
     * 章节号（用户手动输入）
     */
    @Schema(description = "章节号")
    @NotNull(message = "章节号不能为空")
    private Integer chapterNum;


    /**
     * 作者ID
     */
    private Long authorId;


    /**
     * 章节名
     */
    private String chapterName;

    /**
     * 章节内容
     */
    @Schema(description = "章节内容")
    @NotBlank
    @Length(min = 50)
    private String content;

    /**
     * 是否收费;1-收费 0-免费
     */
    @Schema(description = "是否收费;1-收费 0-免费")
    @NotNull
    private Integer isVip;


}
