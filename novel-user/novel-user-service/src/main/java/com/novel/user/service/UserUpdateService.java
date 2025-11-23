package com.novel.user.service;

import com.novel.common.resp.RestResp;
import com.novel.user.dto.UserInfoDto;
import com.novel.user.dto.req.UserInfoUptReqDto;
import com.novel.user.dto.resp.UserInfoRespDto;

public interface UserUpdateService {

    public RestResp<Void> updateUserInfo(UserInfoUptReqDto dto);

}
