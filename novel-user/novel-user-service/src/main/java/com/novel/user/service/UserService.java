package com.novel.user.service;

import com.novel.common.resp.RestResp;
import com.novel.user.dao.entity.UserInfo;
import com.novel.user.dto.req.UserInfoUptReqDto;
import com.novel.user.dto.req.UserLoginReqDto;
import com.novel.user.dto.req.UserRegisterReqDto;
import com.novel.user.dto.resp.UserInfoRespDto;
import com.novel.user.dto.resp.UserLoginRespDto;
import com.novel.user.dto.resp.UserRegisterRespDto;

public interface UserService {

    RestResp<UserRegisterRespDto> register(UserRegisterReqDto dto);
    RestResp<UserLoginRespDto> login(UserLoginReqDto userLoginReqDto);
    RestResp<UserInfoRespDto> getUserInfo(Long id);

}
