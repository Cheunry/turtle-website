package com.novel.user.service;

import com.novel.common.constant.CacheConsts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.util.concurrent.TimeUnit;

/**
 * Token 黑名单服务
 * 用于管理已失效的Token（如登出后的Token）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 将Token加入黑名单
     * 优化：使用Token的MD5 hash作为key，减少Redis内存占用
     *
     * @param token Token字符串
     */
    public void addToBlacklist(String token) {
        // 使用MD5 hash作为key，减少key长度和内存占用
        String tokenHash = DigestUtils.md5DigestAsHex(token.getBytes());
        String key = CacheConsts.TOKEN_BLACKLIST_PREFIX + tokenHash;
        // 设置过期时间为7天（与Token过期时间一致）
        stringRedisTemplate.opsForValue().set(key, "1", 7, TimeUnit.DAYS);
        log.debug("Token已加入黑名单: {} (hash: {})", token.substring(0, Math.min(20, token.length())) + "...", tokenHash);
    }

    /**
     * 检查Token是否在黑名单中
     * 优化：使用Token的MD5 hash作为key进行查询
     *
     * @param token Token字符串
     * @return true-在黑名单中，false-不在黑名单中
     */
    public boolean isBlacklisted(String token) {
        // 使用MD5 hash作为key进行查询
        String tokenHash = DigestUtils.md5DigestAsHex(token.getBytes());
        String key = CacheConsts.TOKEN_BLACKLIST_PREFIX + tokenHash;
        Boolean exists = stringRedisTemplate.hasKey(key);
        return Boolean.TRUE.equals(exists);
    }

    /**
     * 从黑名单中移除Token（通常不需要，因为Token会自动过期）
     * 优化：使用Token的MD5 hash作为key
     *
     * @param token Token字符串
     */
    public void removeFromBlacklist(String token) {
        // 使用MD5 hash作为key
        String tokenHash = DigestUtils.md5DigestAsHex(token.getBytes());
        String key = CacheConsts.TOKEN_BLACKLIST_PREFIX + tokenHash;
        stringRedisTemplate.delete(key);
        log.debug("Token已从黑名单移除: {} (hash: {})", token.substring(0, Math.min(20, token.length())) + "...", tokenHash);
    }

}
