package com.novel.user.manager.cache;

import com.novel.common.constant.CacheConsts;
import com.novel.user.dao.entity.UserInfo;
import com.novel.user.dao.mapper.UserInfoMapper;
import com.novel.user.dto.UserInfoDto;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * 用户信息 缓存管理类
 */
@Service
@Component
@RequiredArgsConstructor
public class UserCacheManager {

    private final UserInfoMapper userInfoMapper;

    /**
     * 查询用户信息，并放入缓存中
     */
    @CachePut(
            cacheManager = CacheConsts.REDIS_CACHE_MANAGER_TYPED,  // 指定使用Redis缓存管理器
            value = CacheConsts.USER_INFO_CACHE_NAME,       // 指定缓存名称
            key = "#userId"
    )
    public UserInfoDto getUserAndPutToCache(Long userId) {
        UserInfo userInfo = userInfoMapper.selectById(userId);
        if(Objects.isNull(userInfo)){
            return null;
        }
        return UserInfoDto.builder()
                .id(userInfo.getId())
                .username(userInfo.getUsername())
                .nickName(userInfo.getNickName())
                .userPhoto(userInfo.getUserPhoto())
                .userSex(userInfo.getUserSex())
                .status(userInfo.getStatus()).build();
    }

    @Cacheable(
            cacheManager = CacheConsts.REDIS_CACHE_MANAGER_TYPED,  // 指定使用Redis缓存管理器
            value = CacheConsts.USER_INFO_CACHE_NAME,       // 指定缓存名称
            key = "#userId"
    )
    public UserInfoDto getUserOrPutToCache(Long userId) {
        UserInfo userInfo = userInfoMapper.selectById(userId);
        if(Objects.isNull(userInfo)){
            return null;
        }
        return UserInfoDto.builder()
                .id(userInfo.getId())
                .username(userInfo.getUsername())
                .nickName(userInfo.getNickName())
                .userPhoto(userInfo.getUserPhoto())
                .userSex(userInfo.getUserSex())
                .status(userInfo.getStatus()).build();
    }


    @CacheEvict(cacheManager = CacheConsts.REDIS_CACHE_MANAGER_TYPED,
            value = CacheConsts.USER_INFO_CACHE_NAME,
            key = "#userId")
    public void evictUserInfo(Long userId) {}

}
