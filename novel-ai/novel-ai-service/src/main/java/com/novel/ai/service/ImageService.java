package com.novel.ai.service;

import com.novel.common.resp.RestResp;

public interface ImageService {

    /**
     * 根据提示词生成图片
     * @param prompt 提示词
     * @return 图片URL
     */
    RestResp<String> generateImage(String prompt);

}
