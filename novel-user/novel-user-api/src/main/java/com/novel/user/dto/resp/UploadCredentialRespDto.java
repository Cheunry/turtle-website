package com.novel.user.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 图片上传凭证响应DTO（前端直传）
 */
@Data
@Builder
public class UploadCredentialRespDto {

    /**
     * 预签名上传URL（用于PUT上传）
     */
    @Schema(description = "预签名上传URL")
    private String presignedUrl;

    /**
     * 最终访问URL（上传成功后的访问地址）
     */
    @Schema(description = "最终访问URL")
    private String finalUrl;

}

