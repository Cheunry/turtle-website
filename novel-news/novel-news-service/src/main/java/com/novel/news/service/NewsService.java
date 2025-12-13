package com.novel.news.service;

import com.novel.common.resp.RestResp;
import com.novel.news.dto.resp.NewsInfoRespDto;
import com.novel.news.dto.resp.NewsReadRespDto;

import java.util.List;

public interface NewsService {

    /**
     * 最新新闻列表查询
     * @return 新闻列表
     */
    RestResp<List<NewsInfoRespDto>> listLatestNews();

    /**
     * 新闻信息查询
     * @param id 新闻ID
     * @return 新闻信息
     */
    RestResp<NewsReadRespDto> getNews(Long id);
}
