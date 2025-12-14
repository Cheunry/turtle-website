package com.novel.resource.service;

import com.novel.common.resp.RestResp;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;

public interface TencentCosService {


    RestResp<String> uploadImageTencent(MultipartFile file);

    RestResp<String> deleteImageTencent(String cosKey);
}