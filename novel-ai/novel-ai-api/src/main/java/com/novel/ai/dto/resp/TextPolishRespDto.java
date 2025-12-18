package com.novel.ai.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "文本润色响应DTO")
public class TextPolishRespDto {

    @Schema(description = "润色后的文本")
    private String polishedText;

    @Schema(description = "润色说明")
    private String explanation;
}