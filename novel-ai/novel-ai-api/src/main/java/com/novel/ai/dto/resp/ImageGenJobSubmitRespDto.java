package com.novel.ai.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "异步生图任务已受理")
public class ImageGenJobSubmitRespDto {

    @Schema(description = "任务ID，用于轮询进度")
    private String jobId;
}
