package com.novel.user.service;

import com.novel.common.resp.RestResp;
import com.novel.user.dto.resp.UserInfoRespDto;

import java.util.List;

public interface UserSelectService {

    /**
     * 用户信息查询
     *
     * @param userId 用户ID
     * @return 用户信息
     */
    RestResp<UserInfoRespDto> getUserInfo(Long userId);

    /**
     * 批量查询用户信息
     *
     * @param userIds 用户ID列表
     * @return 用户信息列表
     */
    RestResp<List<UserInfoRespDto>> listUserInfoByIds(List<Long> userIds);

}
