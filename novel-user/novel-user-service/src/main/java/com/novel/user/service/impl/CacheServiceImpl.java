package com.novel.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.novel.common.constant.CacheConsts;
import com.novel.common.constant.DatabaseConsts;
import com.novel.user.dao.entity.AuthorInfo;
import com.novel.user.dao.entity.UserInfo;
import com.novel.user.dao.mapper.AuthorInfoMapper;
import com.novel.user.dao.mapper.UserInfoMapper;
import com.novel.user.service.CacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
@Slf4j
@Service
@RequiredArgsConstructor
public class CacheServiceImpl implements CacheService {

    private final AuthorInfoMapper authorInfoMapper;
    private final UserInfoMapper userInfoMapper;
    // 使用Redis缓存管理器（type=2的缓存使用Redis）
    @Qualifier("typedJsonCacheManager")
    private final CacheManager cacheManager;

    // ========== 缓存相关方法 ==========

    @Override
    public UserInfo getUserInfo(Long userId) {
        if (userId == null) {
            return null;
        }

        // 从缓存获取
        Cache cache = cacheManager.getCache(CacheConsts.USER_INFO_CACHE_NAME);
        if (cache != null) {
            Cache.ValueWrapper wrapper = cache.get(userId);
            if (wrapper != null && wrapper.get() != null) {
                log.debug("从缓存获取用户信息，userId: {}", userId);
                return (UserInfo) wrapper.get();
            }
        }

        // 缓存未命中，从数据库查询
        UserInfo userInfo = userInfoMapper.selectById(userId);
        if (userInfo != null && cache != null) {
            // 放入缓存
            cache.put(userId, userInfo);
            log.debug("用户信息已放入缓存，userId: {}", userId);
        }

        return userInfo;
    }

    @Override
    public AuthorInfo getAuthorInfoByUserIdFromCache(Long userId) {
        if (userId == null) {
            return null;
        }

        // 从缓存获取（使用userId作为key）
        Cache cache = cacheManager.getCache(CacheConsts.AUTHOR_INFO_CACHE_NAME);
        if (cache != null) {
            Cache.ValueWrapper wrapper = cache.get("userId:" + userId);
            if (wrapper != null && wrapper.get() != null) {
                log.debug("从缓存获取作者信息，userId: {}", userId);
                return (AuthorInfo) wrapper.get();
            }
        }

        // 缓存未命中，从数据库查询
        QueryWrapper<AuthorInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper
                .eq(DatabaseConsts.AuthorInfoTable.COLUMN_USER_ID, userId)
                .last(DatabaseConsts.SqlEnum.LIMIT_1.getSql());
        AuthorInfo authorInfo = authorInfoMapper.selectOne(queryWrapper);

        if (authorInfo != null && cache != null) {
            // 放入缓存（使用userId和authorId作为key）
            cache.put("userId:" + userId, authorInfo);
            cache.put("authorId:" + authorInfo.getId(), authorInfo);
            log.debug("作者信息已放入缓存，userId: {}, authorId: {}", userId, authorInfo.getId());
        }

        return authorInfo;
    }

    @Override
    public void evictUserInfoCache(Long userId) {
        if (userId == null) {
            return;
        }

        Cache cache = cacheManager.getCache(CacheConsts.USER_INFO_CACHE_NAME);
        if (cache != null) {
            cache.evict(userId);
            log.debug("已清除用户信息缓存，userId: {}", userId);
        }
    }

    @Override
    public void evictAuthorInfoCacheByUserId(Long userId) {
        if (userId == null) {
            return;
        }

        Cache cache = cacheManager.getCache(CacheConsts.AUTHOR_INFO_CACHE_NAME);
        if (cache != null) {
            cache.evict("userId:" + userId);
            log.debug("已清除作者信息缓存（通过userId），userId: {}", userId);
        }
    }

    @Override
    public void evictAuthorInfoCacheByAuthorId(Long authorId) {
        if (authorId == null) {
            return;
        }

        Cache cache = cacheManager.getCache(CacheConsts.AUTHOR_INFO_CACHE_NAME);
        if (cache != null) {
            cache.evict("authorId:" + authorId);
            log.debug("已清除作者信息缓存（通过authorId），authorId: {}", authorId);
        }
    }
}
