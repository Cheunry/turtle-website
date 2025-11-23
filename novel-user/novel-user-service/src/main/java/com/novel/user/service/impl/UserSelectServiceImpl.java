package com.novel.user.service.impl;

import com.novel.common.resp.RestResp;
import com.novel.user.dao.entity.UserInfo;
import com.novel.user.dao.mapper.UserInfoMapper;
import com.novel.user.dto.resp.UserInfoRespDto;
import com.novel.user.manager.cache.UserInfoCacheManager;
import com.novel.user.service.UserSelectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class UserSelectServiceImpl implements UserSelectService {

    private final UserInfoMapper userInfoMapper;
    private final UserInfoCacheManager userInfoCacheManager;

    @Override
    public RestResp<UserInfoRespDto> getUserInfo(Long userId) {

        userInfoCacheManager.getUserOrPutToCache(userId);

        UserInfo userInfo = userInfoMapper.selectById(userId);

        if(Objects.isNull(userInfo)) {
            return null;
        }
        return RestResp.ok(UserInfoRespDto.builder()
                .id(userInfo.getId())
                .username(userInfo.getUsername())
                .nickName(userInfo.getNickName())
                .userSex(userInfo.getUserSex())
                .userPhoto(userInfo.getUserPhoto())
                .build()
        );
    }

}
