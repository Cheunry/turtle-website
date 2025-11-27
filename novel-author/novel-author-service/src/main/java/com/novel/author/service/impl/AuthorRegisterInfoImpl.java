package com.novel.author.service.impl;

import com.novel.author.dao.entity.AuthorInfo;
import com.novel.author.dao.mapper.AuthorInfoMapper;
import com.novel.author.dto.AuthorInfoDto;
import com.novel.author.dto.req.AuthorRegisterReqDto;
import com.novel.author.manager.cache.AuthorCacheManager;
import com.novel.author.service.AuthorInfoService;
import com.novel.common.resp.RestResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthorRegisterInfoImpl implements AuthorInfoService {

    private final AuthorInfoMapper authorInfoMapper;
    private final AuthorCacheManager authorCacheManager;


    @Override
    public RestResp<Void> authorRegister(AuthorRegisterReqDto dto) {
        // 校验该用户是否已注册为作家
        AuthorInfoDto author = authorCacheManager.getAuthorInfoByUserId(dto.getUserId());

        if (Objects.nonNull(author)) {
            // 该用户已经是作家，直接返回
            return RestResp.ok();
        }
        // 保存作家注册信息
        AuthorInfo authorInfo = new AuthorInfo();
        authorInfo.setUserId(dto.getUserId());
        authorInfo.setChatAccount(dto.getChatAccount());
        authorInfo.setEmail(dto.getEmail());
        authorInfo.setInviteCode("0");
        authorInfo.setTelPhone(dto.getTelPhone());
        authorInfo.setPenName(dto.getPenName());
        authorInfo.setWorkDirection(dto.getWorkDirection());
        authorInfo.setCreateTime(LocalDateTime.now());
        authorInfo.setUpdateTime(LocalDateTime.now());
        authorInfoMapper.insert(authorInfo);
        return RestResp.ok();
    }


    /**
     * 查询作家状态
     * @param userId 用户ID
     * @return 作家状态
     */
    @Override
    public RestResp<Integer> getStatus(Long userId) {

        AuthorInfoDto authorInfoDto= authorCacheManager.getAuthorInfoByUserId(userId);

        if (Objects.isNull(authorInfoDto)) {
            return RestResp.ok(null);
        }
        return RestResp.ok(authorInfoDto.getStatus());

    }

}
