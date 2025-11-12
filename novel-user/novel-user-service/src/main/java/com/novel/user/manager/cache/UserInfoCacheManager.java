package com.novel.user.manager.cache;

import com.novel.common.constant.CacheConsts;
import com.novel.user.dao.entity.UserInfo;
import com.novel.user.dao.mapper.UserInfoMapper;
import com.novel.user.dto.UserInfoDto;
import com.novel.user.dto.resp.UserInfoRespDto;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 用户信息 缓存管理类
 */

@Component
@RequiredArgsConstructor
public class UserInfoCacheManager {
    private final UserInfoMapper userInfoMapper;

    /**
     * 查询用户信息，并放入缓存中
     */
    @Cacheable(
            cacheManager = CacheConsts.REDIS_CACHE_MANAGER,  // 指定使用Redis缓存管理器
            value = CacheConsts.USER_INFO_CACHE_NAME         // 指定缓存名称
    )
    public UserInfoDto getUser(Long userId) {
        UserInfo userInfo = userInfoMapper.selectById(userId);
        if(Objects.isNull(userInfo)){
            return null;
        }
        return UserInfoDto.builder()
                .id(userInfo.getId())
                .status(userInfo.getStatus()).build();
    }

}
