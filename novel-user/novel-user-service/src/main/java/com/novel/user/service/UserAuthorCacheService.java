package com.novel.user.service;

import com.novel.user.dao.entity.AuthorInfo;
import com.novel.user.dao.entity.UserInfo;

/**
 * 用户和作者信息缓存服务
 * 用于优化AuthInterceptor中的数据库查询
 */
public interface UserAuthorCacheService {

    /**
     * 根据用户ID获取用户信息（优先从缓存获取）
     * @param userId 用户ID
     * @return 用户信息，如果不存在返回null
     */
    UserInfo getUserInfo(Long userId);

    /**
     * 根据用户ID获取作者信息（优先从缓存获取）
     * @param userId 用户ID
     * @return 作者信息，如果不存在返回null
     */
    AuthorInfo getAuthorInfoByUserId(Long userId);

    /**
     * 清除用户信息缓存
     * @param userId 用户ID
     */
    void evictUserInfoCache(Long userId);

    /**
     * 清除作者信息缓存（通过用户ID）
     * @param userId 用户ID
     */
    void evictAuthorInfoCacheByUserId(Long userId);

    /**
     * 清除作者信息缓存（通过作者ID）
     * @param authorId 作者ID
     */
    void evictAuthorInfoCache(Long authorId);
}

