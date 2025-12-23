package com.novel.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.novel.user.dao.entity.AuthorInfo;
import com.novel.user.dao.mapper.AuthorInfoMapper;
import com.novel.user.dto.AuthorInfoDto;
import com.novel.user.dto.req.AuthorRegisterReqDto;
import com.novel.user.service.AuthorInfoService;
import com.novel.common.constant.DatabaseConsts;
import com.novel.common.resp.RestResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthorInfoServiceImpl implements AuthorInfoService {

    private final AuthorInfoMapper authorInfoMapper;

    /**
     * 作家注册
     * @param dto 作家注册请求DTO
     * @return Void
     */
    @Override
    public RestResp<Void> authorRegister(AuthorRegisterReqDto dto) {
        // 校验该用户是否已注册为作家
        AuthorInfoDto author = getAuthorInfoByUserId(dto.getUserId());

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

        AuthorInfoDto authorInfoDto= getAuthorInfoByUserId(userId);

        if (Objects.isNull(authorInfoDto)) {
            return RestResp.ok(null);
        }
        return RestResp.ok(authorInfoDto.getStatus());

    }

    /**
     * 查询作家信息
     * @param userId 用户ID
     * @return 作家基础信息DTO
     */
    public AuthorInfoDto getAuthorInfoByUserId(Long userId) {
        QueryWrapper<AuthorInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper
                .eq(DatabaseConsts.AuthorInfoTable.COLUMN_USER_ID, userId)
                .last(DatabaseConsts.SqlEnum.LIMIT_1.getSql());
        AuthorInfo authorInfo = authorInfoMapper.selectOne(queryWrapper);
        if (Objects.isNull(authorInfo)) {
            return null;
        }
        return AuthorInfoDto.builder()
                .id(authorInfo.getId())
                .penName(authorInfo.getPenName())
                .status(authorInfo.getStatus()).build();
    }

}

