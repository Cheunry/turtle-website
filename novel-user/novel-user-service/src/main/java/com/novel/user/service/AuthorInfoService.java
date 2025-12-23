package com.novel.user.service;

import com.novel.user.dto.req.AuthorRegisterReqDto;
import com.novel.common.resp.RestResp;
import org.springframework.stereotype.Service;

@Service
public interface AuthorInfoService {

    RestResp<Void> authorRegister(AuthorRegisterReqDto dto);

    RestResp<Integer> getStatus(Long userId);

}

