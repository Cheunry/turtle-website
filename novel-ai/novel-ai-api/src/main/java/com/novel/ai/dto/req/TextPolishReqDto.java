package com.novel.ai.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "文本润色请求DTO")
public class TextPolishReqDto {

    @Schema(description = "待润色的文本内容")
    @NotBlank(message = "文本内容不能为空")
    private String text;

    @Schema(description = "润色风格，如：正式、简洁、生动等", example = "正式")
    private String style;

    @Schema(description = "润色要求", example = "保持原意，提升表达")
    private String requirement;
}