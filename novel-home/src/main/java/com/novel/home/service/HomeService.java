package com.novel.home.service;

import com.novel.common.resp.RestResp;
import com.novel.home.dto.resp.HomeBookRespDto;
import com.novel.home.dto.resp.HomeFriendLinkRespDto;

import java.util.List;

public interface HomeService {

    /**
     * 查询首页小说展示列表
     * @return 首页小说展示列表的rest响应结果
     */
    RestResp<List<HomeBookRespDto>> listHomeBook();

    /**
     * 查询首页友情链接列表
     * @return 首页友情链接列表的rest响应结果
     */
    RestResp<List<HomeFriendLinkRespDto>>  listHomeFriendLink();
}
