package com.novel.user.service;

import com.novel.common.resp.RestResp;
import com.novel.user.dto.UserInfoDto;
import com.novel.user.dto.req.UserLoginReqDto;
import com.novel.user.dto.resp.UserLoginRespDto;

public interface UserLoginService {
    /**
     * 用户登录
     *
     * @param dto 登录参数
     * @return JWT + 昵称
     */
    RestResp<UserLoginRespDto> login(UserLoginReqDto dto);

}
