package com.novel.user.controller.front;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.novel.common.constant.ApiRouterConsts;
import com.novel.common.constant.CacheConsts;
import com.novel.common.manager.TencentCosManager;
import com.novel.common.resp.RestResp;
import com.novel.common.util.ImgVerifyCodeUtils;
import com.novel.user.dto.resp.ImgVerifyCodeRespDto;
import com.novel.user.dto.resp.UploadCredentialRespDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.Date;

@Slf4j
@Tag(name = "ResourceController", description = "前台门户-资源模块")
@RestController
@RequestMapping(ApiRouterConsts.API_FRONT_RESOURCE_URL_PREFIX)
@RequiredArgsConstructor
public class ResourceController {

    private final StringRedisTemplate stringRedisTemplate;
    private final TencentCosManager tencentCosManager;

    @Operation(summary = "获取图片验证码接口")
    @GetMapping("img_verify_code")
    public RestResp<ImgVerifyCodeRespDto> getImgVerifyCode() throws IOException {
        String sessionId = IdWorker.get32UUID();
        String verifyCode = ImgVerifyCodeUtils.getRandomVerifyCode(4);
        String img = ImgVerifyCodeUtils.genVerifyCodeImg(verifyCode);
        stringRedisTemplate.opsForValue().set(CacheConsts.IMG_VERIFY_CODE_CACHE_KEY + sessionId,
                verifyCode, Duration.ofMinutes(5));
        
        return RestResp.ok(ImgVerifyCodeRespDto.builder()
                .sessionId(sessionId)
                .img(img)
                .build());
    }

    @Operation(summary = "获取图片上传签名（前端直传）")
    @GetMapping("/image/credential")
    public RestResp<UploadCredentialRespDto> getUploadCredential(
            @Parameter(description = "文件名后缀") @RequestParam("ext") String ext) {
        // 构造 Key
        // resource/2023/12/29/uuid.png
        String date = new java.text.SimpleDateFormat("yyyy/MM/dd").format(new Date());
        String key = String.format("resource/%s/%s.%s", date, IdWorker.get32UUID(), ext);
        
        // 5分钟有效
        Date expiration = new Date(System.currentTimeMillis() + 5 * 60 * 1000);
        String presignedUrl = tencentCosManager.generatePresignedUrl(key, expiration);
        String finalUrl = tencentCosManager.getUrl(key);
        
        log.info("生成上传凭证，扩展名: {}, Key: {}, 最终URL: {}", ext, key, finalUrl);
        
        return RestResp.ok(UploadCredentialRespDto.builder()
                .presignedUrl(presignedUrl)
                .finalUrl(finalUrl)
                .build());
    }
    
    @Operation(summary = "图片上传接口（兼容旧代码，建议使用前端直传）")
    @PostMapping("/image")
    public RestResp<String> uploadImage(@RequestParam("file") MultipartFile file) throws IOException {
        log.info("收到图片上传请求，文件名: {}, 大小: {} bytes", file.getOriginalFilename(), file.getSize());
        String date = new java.text.SimpleDateFormat("yyyy/MM/dd").format(new Date());
        String ext = org.apache.commons.io.FilenameUtils.getExtension(file.getOriginalFilename());
        String key = String.format("resource/%s/%s.%s", date, IdWorker.get32UUID(), ext);
        
        String url = tencentCosManager.uploadFile(file.getInputStream(), file.getSize(), file.getContentType(), key);
        log.info("图片上传成功，Key: {}, URL: {}", key, url);
        return RestResp.ok(url);
    }

    @Operation(summary = "图片转存接口（通过URL上传）")
    @PostMapping("/image/url")
    public RestResp<String> uploadImageFromUrl(
            @Parameter(description = "图片URL") @RequestParam("url") String url) {
        return RestResp.ok(tencentCosManager.uploadImageFromUrl(url));
    }
}

