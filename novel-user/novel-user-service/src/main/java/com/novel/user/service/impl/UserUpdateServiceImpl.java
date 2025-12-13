package com.novel.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.novel.common.resp.RestResp;
import com.novel.user.dao.entity.UserInfo;
import com.novel.user.dao.mapper.UserInfoMapper;
import com.novel.user.dto.req.UserInfoUptReqDto;
import com.novel.user.service.UserUpdateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserUpdateServiceImpl implements UserUpdateService {

    private final UserInfoMapper userInfoMapper;


    @Override
    @Transactional(rollbackFor = Exception.class)
    public RestResp<Void> updateUserInfo(UserInfoUptReqDto dto) {

        // 使用 LambdaUpdateWrapper 精确更新字段，避免实体 updateById 的潜在问题
        LambdaUpdateWrapper<UserInfo> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(UserInfo::getId, dto.getUserId());

        if (dto.getNickName() != null) {
            updateWrapper.set(UserInfo::getNickName, dto.getNickName());
        }
        if (dto.getUserPhoto() != null) {
            updateWrapper.set(UserInfo::getUserPhoto, dto.getUserPhoto());
        }
        if (dto.getUserSex() != null) {
            updateWrapper.set(UserInfo::getUserSex, dto.getUserSex());
        }

        updateWrapper.set(UserInfo::getUpdateTime, LocalDateTime.now());

        userInfoMapper.update(null, updateWrapper);

        return RestResp.ok();
    }

}
