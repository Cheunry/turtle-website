package com.novel.common.manager;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.http.HttpMethodName;
import com.qcloud.cos.model.GeneratePresignedUrlRequest;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.region.Region;
import jakarta.annotation.PostConstruct;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "tencent.cos", name = "secret-id")
public class TencentCosManager {

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
    @SneakyThrows
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
     * 上传文件（流式）
     */
    public String uploadFile(InputStream inputStream, long size, String contentType, String key) {
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(size);
            metadata.setContentType(contentType);
            PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, key, inputStream, metadata);
            cosClient.putObject(putObjectRequest);
            
            return getUrl(key);
        } catch (Exception e) {
            log.error("文件上传失败", e);
            throw new RuntimeException("文件上传失败", e);
        }
    }

    /**
     * 生成预签名上传 URL (用于前端直传)
     * @param key 存储路径
     * @param expiration 过期时间
     * @return 预签名 URL 字符串
     */
    public String generatePresignedUrl(String key, Date expiration) {
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucketName, key, HttpMethodName.PUT);
        request.setExpiration(expiration);
        
        // 设置请求头，允许前端设置 Content-Type
        request.addRequestParameter("Content-Type", "*");
        
        URL url = cosClient.generatePresignedUrl(request);
        return url.toString();
    }
    
    /**
     * 从 URL 下载并转存到 COS
     * @param imageUrl 图片 URL
     * @return COS 访问 URL
     */
    public String uploadImageFromUrl(String imageUrl) {
        try {
            // 1. 从 URL 下载图片
            URL url = new URL(imageUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setRequestMethod("GET");
            
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new RuntimeException("下载图片失败，HTTP状态码: " + responseCode);
            }
            
            String contentType = connection.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                contentType = "image/jpeg"; // 默认类型
            }
            
            // 2. 读取图片数据
            byte[] fileBytes;
            try (InputStream inputStream = connection.getInputStream()) {
                fileBytes = inputStream.readAllBytes();
            }
            
            // 3. 构造存储 Key
            String date = new java.text.SimpleDateFormat("yyyy/MM/dd").format(new Date());
            String ext = getExtensionFromUrl(imageUrl);
            String key = String.format("resource/%s/%s.%s", date, 
                    java.util.UUID.randomUUID().toString().replace("-", ""), ext);
            
            // 4. 上传到 COS
            try (ByteArrayInputStream uploadStream = new ByteArrayInputStream(fileBytes)) {
                ObjectMetadata metadata = new ObjectMetadata();
                metadata.setContentLength(fileBytes.length);
                metadata.setContentType(contentType);
                PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, key, uploadStream, metadata);
                cosClient.putObject(putObjectRequest);
                
                log.info("URL图片转存成功，原URL: {}, COS Key: {}", imageUrl, key);
                return getUrl(key);
            }
        } catch (Exception e) {
            log.error("URL图片转存失败，URL: {}", imageUrl, e);
            throw new RuntimeException("URL图片转存失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 从 URL 中提取文件扩展名
     */
    private String getExtensionFromUrl(String url) {
        try {
            String path = new URL(url).getPath();
            int lastDot = path.lastIndexOf('.');
            if (lastDot > 0 && lastDot < path.length() - 1) {
                String ext = path.substring(lastDot + 1).toLowerCase();
                // 只保留常见的图片扩展名
                if (ext.matches("jpg|jpeg|png|gif|webp|bmp")) {
                    return ext.equals("jpeg") ? "jpg" : ext;
                }
            }
        } catch (Exception e) {
            log.warn("无法从URL提取扩展名: {}", url);
        }
        return "jpg"; // 默认 jpg
    }
    
    /**
     * 获取访问 URL
     */
    public String getUrl(String key) {
        if (baseUrl.endsWith("/")) {
            return baseUrl + key;
        } else {
            return baseUrl + "/" + key;
        }
    }
}

