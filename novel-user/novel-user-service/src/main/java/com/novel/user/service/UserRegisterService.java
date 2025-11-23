package com.novel.user.service;

import com.novel.common.resp.RestResp;
import com.novel.user.dto.req.UserRegisterReqDto;
import com.novel.user.dto.resp.UserRegisterRespDto;

public interface UserRegisterService {

    /**
     * 用户注册
     *
     * @param dto 注册参数
     * @return JWT
     */
    RestResp<UserRegisterRespDto> register(UserRegisterReqDto dto);



}
