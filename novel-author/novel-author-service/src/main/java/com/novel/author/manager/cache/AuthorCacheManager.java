package com.novel.author.manager.cache;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.novel.author.dao.entity.AuthorInfo;
import com.novel.author.dao.mapper.AuthorInfoMapper;
import com.novel.author.dto.AuthorInfoDto;
import com.novel.common.constant.DatabaseConsts;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@RequiredArgsConstructor
public class AuthorCacheManager {

    private final AuthorInfoMapper authorInfoMapper;

    /**
     * 查询作家信息(选择性放缓存)
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
