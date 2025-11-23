package com.novel.home.manager.cache;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.novel.common.constant.CacheConsts;
import com.novel.common.constant.DatabaseConsts;
import com.novel.home.dao.entity.HomeFriendLink;
import com.novel.home.dao.mapper.HomeFriendLinkMapper;
import com.novel.home.dto.resp.HomeFriendLinkRespDto;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import java.util.List;
import org.springframework.cache.annotation.CachePut;

@Component
@RequiredArgsConstructor
public class FriendLinkCacheManager {

    private final HomeFriendLinkMapper friendLinkMapper;

    /**
     * 友情链接列表查询，并放入缓存中
     */
    @Cacheable(cacheManager = CacheConsts.REDIS_CACHE_MANAGER_PLAIN, value = CacheConsts.HOME_FRIEND_LINK_CACHE_NAME)
    public List<HomeFriendLinkRespDto>  listFriendLinks() {
        // 从友情链接表中查询出友情链接列表
        QueryWrapper<HomeFriendLink> queryWrapper = new QueryWrapper<>();
        queryWrapper.orderByDesc(DatabaseConsts.CommonColumnEnum.SORT.getName());

        return friendLinkMapper.selectList(queryWrapper).stream().map(v -> {
            HomeFriendLinkRespDto dto = new HomeFriendLinkRespDto();
            dto.setLinkName(v.getLinkName());
            dto.setLinkUrl(v.getLinkUrl());
            return dto;
        }).toList();

    }

    /**
     * 更新缓存
     */
    @CachePut(cacheManager = CacheConsts.REDIS_CACHE_MANAGER_PLAIN,
            value = CacheConsts.HOME_FRIEND_LINK_CACHE_NAME)
    public List<HomeFriendLinkRespDto> updateFriendLinkCache() {
        // 直接查询最新数据并更新缓存
        return listFriendLinks();
    }

    @CacheEvict(cacheManager = CacheConsts.REDIS_CACHE_MANAGER_PLAIN,
            value = CacheConsts.HOME_FRIEND_LINK_CACHE_NAME)
    public void evictCache() {}

}
