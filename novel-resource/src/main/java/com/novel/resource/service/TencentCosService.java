package com.novel.resource.service;

import com.novel.common.resp.RestResp;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;

public interface TencentCosService {


    /**
     * 上传图片到 COS 存储桶
     * @param file 要上传的图片文件
     * @return 完整的图片URL
     */
    RestResp<String> uploadImageTencent(MultipartFile file);

    /**
     * 通过URL上传图片到 COS 对象存储
     * @param imageUrl 图片URL
     * @return 上传后的COS URL
     */
    RestResp<String> uploadImageFromUrl(String imageUrl);

    /**
     * 删除COS中的图片
     * @param cosKey 要删除的图片
     * @return 删除结果
     */
    RestResp<String> deleteImageTencent(String cosKey);
}
