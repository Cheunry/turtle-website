package com.novel.user.service;

import com.novel.common.resp.RestResp;
import com.novel.user.dto.resp.UserInfoRespDto;

public interface UserSelectService {

    /**
     * 用户信息查询
     *
     * @param userId 用户ID
     * @return 用户信息
     */
    RestResp<UserInfoRespDto> getUserInfo(Long userId);

}
