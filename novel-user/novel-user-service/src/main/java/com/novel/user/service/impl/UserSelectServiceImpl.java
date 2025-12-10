package com.novel.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.novel.common.constant.DatabaseConsts;
import com.novel.common.resp.RestResp;
import com.novel.user.dao.entity.UserInfo;
import com.novel.user.dao.mapper.UserInfoMapper;
import com.novel.user.dto.resp.UserInfoRespDto;
import com.novel.user.manager.cache.UserCacheManager;
import com.novel.user.service.UserSelectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserSelectServiceImpl implements UserSelectService {

    private final UserInfoMapper userInfoMapper;
    private final UserCacheManager userCacheManager;

    @Override
    public RestResp<UserInfoRespDto> getUserInfo(Long userId) {

        userCacheManager.getUserOrPutToCache(userId);

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

    @Override
    public RestResp<List<UserInfoRespDto>> listUserInfoByIds(List<Long> userIds) {
        QueryWrapper<UserInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.in(DatabaseConsts.CommonColumnEnum.ID.getName(), userIds);
        return RestResp.ok(
                userInfoMapper.selectList(queryWrapper).stream().map(v -> UserInfoRespDto.builder()
                        .id(v.getId())
                        .username(v.getUsername())
                        .userPhoto(v.getUserPhoto())
                        .build()).collect(Collectors.toList()));
    }

}
