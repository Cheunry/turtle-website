package com.novel.user.service.impl;

import com.novel.common.resp.RestResp;
import com.novel.user.dao.entity.UserInfo;
import com.novel.user.dao.mapper.UserInfoMapper;
import com.novel.user.dto.req.UserInfoUptReqDto;
import com.novel.user.manager.cache.UserCacheManager;
import com.novel.user.service.UserUpdateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
@Slf4j
public class UserUpdateServiceImpl implements UserUpdateService {

    private final UserInfoMapper userInfoMapper;
    private final UserCacheManager userCacheManager;

    public UserUpdateServiceImpl(UserInfoMapper userInfoMapper, UserCacheManager userCacheManager) {
        this.userInfoMapper = userInfoMapper;
        this.userCacheManager = userCacheManager;
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public RestResp<Void> updateUserInfo(UserInfoUptReqDto dto) {

        UserInfo userInfo = new UserInfo();

        // 更新数据库
        userInfo.setId(dto.getUserId());
        userInfo.setNickName(dto.getNickName());
        userInfo.setUserPhoto(dto.getUserPhoto());
        userInfo.setUserSex(dto.getUserSex());
        userInfo.setUpdateTime(LocalDateTime.now());
        userInfoMapper.updateById(userInfo);

        //  删除redis中对应的key
        userCacheManager.evictUserInfo(userInfo.getId());

        //  向redis中放入新的key
        userCacheManager.getUserAndPutToCache(userInfo.getId());

        return RestResp.ok();
    }

}
