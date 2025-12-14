package com.novel.resource.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.novel.common.constant.ErrorCodeEnum;
import com.novel.common.resp.RestResp;
import com.novel.config.exception.BusinessException;
import com.novel.resource.service.TencentCosService;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.exception.CosServiceException;
import com.qcloud.cos.model.DeleteObjectRequest;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.region.Region;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

@Slf4j
@Service
public class TencentCosServiceImpl implements TencentCosService {

    // COS 配置
    @Value("${tencent.cos.secret-id}")
    private String secretId;
    @Value("${tencent.cos.secret-key}")
    private String secretKey;
    @Value("${tencent.cos.bucket-name}")
    private String bucketName;
    @Value("${tencent.cos.region}")
    private String region;
    @Value("${tencent.cos.base-url}")
    private String baseUrl;

    private COSClient cosClient;

    /**
     * 初始化 COSClient (在 Spring Bean 初始化时执行)
     */
    @PostConstruct
    public void init() {
        try {
            log.info("初始化腾讯 COS 客户端...");

            COSCredentials cred = new BasicCOSCredentials(secretId, secretKey);
            ClientConfig clientConfig = new ClientConfig(new Region(region));
            this.cosClient = new COSClient(cred, clientConfig);

            log.info("腾讯 COS 客户端初始化成功。");
        } catch (Exception e) {

            log.error("腾讯 COS 客户端初始化失败！请检查配置。", e);
            throw new RuntimeException("COS 客户端初始化失败", e);
        }
    }

    /**
     * 上传图片到 COS 对象存储
     * 使用 @SneakyThrows 简化异常处理
     */
    @SneakyThrows // 标记方法，将检查型异常转换为运行时异常
    @Override
    public RestResp<String> uploadImageTencent(MultipartFile file) {

        if (file.isEmpty() || file.getOriginalFilename() == null) {
            throw new BusinessException(ErrorCodeEnum.USER_UPLOAD_FILE_ERROR);
        }

        byte[] fileBytes = file.getBytes(); // 使用 @SneakyThrows 隐藏 IOException

        // 使用 ByteArrayInputStream校验图片类型
        try (InputStream checkStream = new ByteArrayInputStream(fileBytes)) {
            if (Objects.isNull(ImageIO.read(checkStream))) {
                throw new BusinessException(ErrorCodeEnum.USER_UPLOAD_FILE_TYPE_NOT_MATCH);
            }
        } catch (IOException e) {
            log.error("图片文件校验失败", e);
            throw new BusinessException(ErrorCodeEnum.USER_UPLOAD_FILE_TYPE_NOT_MATCH);
        }

        // 构造COS存储Key
        LocalDateTime now = LocalDateTime.now();

        // 构造存储Key
        String prefix = "resource/"
                + now.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));

        // 使用FilenameUtils简洁地获取文件扩展名
        String extension = FilenameUtils.getExtension(file.getOriginalFilename());

        // 构造唯一的COS Key
        String cosKey = String.format("%s/%s.%s", prefix, IdWorker.get32UUID(), extension);

        // 调用COS服务进行上传
        try (InputStream uploadStream = new ByteArrayInputStream(fileBytes)) {

            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.getSize());
            metadata.setContentType(file.getContentType());

            PutObjectRequest putObjectRequest =
                    new PutObjectRequest(bucketName, cosKey, uploadStream, metadata);

            // 执行上传
            cosClient.putObject(putObjectRequest);

            // 构造完整的 URL
            // 确保 baseUrl 和 cosKey 之间有斜杠
            String uploadedUrl;
            if (baseUrl.endsWith("/")) {
                uploadedUrl = baseUrl + cosKey;
            } else {
                uploadedUrl = baseUrl + "/" + cosKey;
            }

            log.info("图片上传成功，COS Key: {}, URL: {}", cosKey, uploadedUrl);
            return RestResp.ok(uploadedUrl);

        } catch (Exception e) {
            log.error("上传图片到 COS 失败，Key: {}", cosKey, e);
            // 捕获COS SDK可能抛出的SDKException或ClientException，转换为业务异常
            throw new BusinessException(ErrorCodeEnum.USER_UPLOAD_FILE_ERROR);
        }
    }


    /**
     * 从对象存储中删除图片
     * @param fileUrl 要删除图片的完整 URL
     * @return Void
     */
    @Override
    public RestResp<String> deleteImageTencent(String fileUrl) {
        String cosKey;
        try {
            // 从 URL 中解析出 COS Key
            cosKey = extractCosKeyFromUrl(fileUrl);
        } catch (BusinessException e) {
            // 捕捉参数解析失败的异常，返回错误
            return RestResp.fail(e.getErrorCodeEnum());
        }

        // 构造删除请求并执行
        try {
            DeleteObjectRequest deleteObjectRequest = new DeleteObjectRequest(bucketName, cosKey);
            cosClient.deleteObject(deleteObjectRequest);

            log.info("图片删除成功，URL: {}, COS Key: {}", fileUrl, cosKey);
            return RestResp.ok("图片删除成功");

        } catch (CosServiceException e) {
            // 处理 COS 服务端异常
            if ("NoSuchKey".equalsIgnoreCase(e.getErrorCode())) {
                log.warn("尝试删除的对象不存在，URL: {}, COS Key: {}", fileUrl, cosKey);
                // 认为删除操作“成功”完成（幂等性）
                return RestResp.ok("对象不存在，无需删除");
            }

            log.error("从 COS 删除图片失败，URL: {}, COS Key: {}, 错误码: {}", fileUrl, cosKey, e.getErrorCode(), e);
            throw new BusinessException(ErrorCodeEnum.THIRD_SERVICE_COS_ERROR);

        } catch (CosClientException e) {
            // 处理 COS 客户端异常
            log.error("从 COS 删除图片失败，URL: {}, COS Key: {}", fileUrl, cosKey, e);
            throw new BusinessException(ErrorCodeEnum.THIRD_SERVICE_COS_ERROR);
        }
    }

    /**
     * 辅助方法：从完整的 URL 中提取 COS Key
     * @param fileUrl 完整的图片访问 URL
     * @return COS 对象键 (Key)
     */
    private String extractCosKeyFromUrl(String fileUrl) {
        if (fileUrl == null || fileUrl.trim().isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.USER_IMAGE_URL_NULL);
        }

        // 规范化 baseUrl：确保它以 "/" 结尾
        String sanitizedBaseUrl = baseUrl;
        if (!sanitizedBaseUrl.endsWith("/")) {
            sanitizedBaseUrl += "/";
        }

        // 检查 fileUrl 是否以 baseUrl 开头，并截取后面的部分作为 Key
        if (fileUrl.startsWith(sanitizedBaseUrl)) {
            String cosKey = fileUrl.substring(sanitizedBaseUrl.length());
            if (cosKey.isEmpty()) {
                throw new BusinessException(ErrorCodeEnum.USER_IMAGE_URL_BROKEN);
            }
            return cosKey;
        }

        log.error("图片 URL [{}] 与配置的 baseUrl [{}] 不匹配。", fileUrl, baseUrl);
        throw new BusinessException(ErrorCodeEnum.USER_IMAGE_URL_FALSE);
    }

}
