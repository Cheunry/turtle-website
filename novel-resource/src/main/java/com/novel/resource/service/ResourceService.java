package com.novel.resource.service;

import com.novel.common.resp.RestResp;
import com.novel.resource.dto.resp.ImgVerifyCodeRespDto;
import java.io.IOException;

/**
 * 资源（图片/视频/文档）相关服务类
 */
public interface ResourceService {

    /**
     * 获取图片验证码
     *
     * @throws IOException 验证码图片生成失败
     * @return Base64编码的图片
     */
    RestResp<ImgVerifyCodeRespDto> getImgVerifyCode() throws IOException;

}
