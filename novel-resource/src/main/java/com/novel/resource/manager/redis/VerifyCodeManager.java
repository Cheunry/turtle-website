package com.novel.resource.manager.redis;

import com.novel.common.constant.CacheConsts;
import com.novel.resource.util.ImgVerifyCodeUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;

/**
 * 验证码 管理类
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class VerifyCodeManager {

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 生成图形验证码，并放入 Redis 中
     */
    public String genImgVerifyCode(String sessionId) throws IOException {

        String verifyCode = ImgVerifyCodeUtils.getRandomVerifyCode(4);     //实时生成验证码文本
        String img = ImgVerifyCodeUtils.genVerifyCodeImg(verifyCode);           //基于文本生成验证码的图片

        stringRedisTemplate.opsForValue().set(CacheConsts.IMG_VERIFY_CODE_CACHE_KEY + sessionId,
                verifyCode, Duration.ofMinutes(5));//将验证码文本存入Redis

        return img;
    }

}
