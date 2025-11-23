package com.novel.resource.service.impl;


import com.novel.common.resp.RestResp;
import com.novel.resource.dto.resp.ImgVerifyCodeRespDto;
import com.novel.resource.manager.redis.VerifyCodeManager;
import com.novel.resource.service.ResourceService;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.io.IOException;


/**
 * 资源（图片/视频/文档）相关服务实现类
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ResourceServiceImpl implements ResourceService {

    private final VerifyCodeManager verifyCodeManager;


    @Override
    public RestResp<ImgVerifyCodeRespDto> getImgVerifyCode() throws IOException {

        String sessionId = IdWorker.get32UUID();

        return RestResp.ok(ImgVerifyCodeRespDto.builder()
                .sessionId(sessionId)
                .img(verifyCodeManager.genImgVerifyCode(sessionId))
                .build());
    }

}
