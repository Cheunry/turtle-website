package com.novel.home.service.impl;

import com.novel.common.resp.RestResp;
import com.novel.home.dto.resp.HomeBookRespDto;
import com.novel.home.dto.resp.HomeFriendLinkRespDto;
import com.novel.home.manager.cache.FriendLinkCacheManager;
import com.novel.home.manager.cache.HomeBookCacheManeger;
import com.novel.home.service.HomeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;

/**
 * 首页模块-服务实现类
 */
@Service
@RequiredArgsConstructor
public class HomeServiceImpl implements HomeService {
    private final HomeBookCacheManeger homeBookCacheManeger;
    private final FriendLinkCacheManager friendLinkCacheManager;

    @Override
    public RestResp<List<HomeBookRespDto>> listHomeBook() {
        List<HomeBookRespDto> list  = homeBookCacheManeger.listHomeBooks();
        if(CollectionUtils.isEmpty(list)) {
            homeBookCacheManeger.evictCache();
        }
        return RestResp.ok(list);
    }

    @Override
    public RestResp<List<HomeFriendLinkRespDto>> listHomeFriendLink() {
        List<HomeFriendLinkRespDto> list  = friendLinkCacheManager.listFriendLinks();
        if(CollectionUtils.isEmpty(list)) {
            friendLinkCacheManager.evictCache();
        }
        return RestResp.ok(list);
    }
}
