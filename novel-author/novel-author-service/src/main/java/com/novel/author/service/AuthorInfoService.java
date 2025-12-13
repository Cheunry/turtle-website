package com.novel.author.service;

import com.novel.author.dto.req.AuthorRegisterReqDto;
import com.novel.common.auth.UserHolder;
import com.novel.common.resp.RestResp;
import org.springframework.stereotype.Service;

@Service
public interface AuthorInfoService {

    RestResp<Void> authorRegister(AuthorRegisterReqDto dto);

    RestResp<Integer> getStatus(Long userId);

}
