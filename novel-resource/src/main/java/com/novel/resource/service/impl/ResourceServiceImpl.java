package com.novel.resource.service.impl;


import com.novel.common.constant.CacheConsts;
import com.novel.common.resp.RestResp;
import com.novel.resource.dto.resp.ImgVerifyCodeRespDto;
import com.novel.resource.service.ResourceService;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.novel.resource.util.ImgVerifyCodeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.time.Duration;


/**
 * 资源（图片/视频/文档）相关服务实现类
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ResourceServiceImpl implements ResourceService {

    private final StringRedisTemplate stringRedisTemplate;


    /**
     * 获取图片验证码及其对应的 Session ID。
     * @return 包含 Session ID 和 Base64 格式验证码图片的统一响应对象。
     * @throws IOException 如果生成图片过程中发生 IO 错误。
     * @see ImgVerifyCodeRespDto
     */
    @Override
    public RestResp<ImgVerifyCodeRespDto> getImgVerifyCode() throws IOException {

        String sessionId = IdWorker.get32UUID();

        return RestResp.ok(ImgVerifyCodeRespDto.builder()
                .sessionId(sessionId)
                .img(genImgVerifyCode(sessionId))
                .build());
    }

    /**
     * **生成图形验证码：**
     *
     * <p>实时生成一个随机验证码文本，将其存储到 Redis 中（设置 5 分钟有效期），
     * 并基于该文本生成 Base64 编码的验证码图片。</p>
     *
     * @param sessionId 本次验证码请求的唯一标识，用于作为 Redis Key 的一部分
     * @return Base64 编码的验证码图片字符串
     * @throws IOException 如果生成图片过程中发生 IO 错误
     */
    public String genImgVerifyCode(String sessionId) throws IOException {

        String verifyCode = ImgVerifyCodeUtils.getRandomVerifyCode(4);     //实时生成验证码文本
        String img = ImgVerifyCodeUtils.genVerifyCodeImg(verifyCode);           //基于文本生成验证码的图片

        stringRedisTemplate.opsForValue().set(CacheConsts.IMG_VERIFY_CODE_CACHE_KEY + sessionId,
                verifyCode, Duration.ofMinutes(5));//将验证码文本存入Redis

        return img;
    }

}
