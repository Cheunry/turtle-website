package com.novel.news.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.novel.common.constant.DatabaseConsts;
import com.novel.common.resp.RestResp;
import com.novel.news.dao.entity.NewsInfo;
import com.novel.news.dao.mapper.NewsInfoMapper;
import com.novel.news.dto.resp.NewsInfoRespDto;
import com.novel.news.dto.resp.NewsReadRespDto;
import com.novel.news.service.NewsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
@RequiredArgsConstructor
public class NewsServiceImpl implements NewsService {


    private final NewsInfoMapper newsInfoMapper;

    @Override
    public RestResp<List<NewsInfoRespDto>> listLatestNews() {

        // 从新闻信息表中查询出最新发布的两条新闻
        QueryWrapper<NewsInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.orderByDesc(DatabaseConsts.CommonColumnEnum.CREATE_TIME.getName())
                .last(DatabaseConsts.SqlEnum.LIMIT_2.getSql());
        List<NewsInfoRespDto> newsInfoRespDtoList = newsInfoMapper.selectList(queryWrapper).stream().map(v -> NewsInfoRespDto.builder()
                .id(v.getId())
                .categoryId(v.getCategoryId())
                .categoryName(v.getCategoryName())
                .title(v.getTitle())
                .sourceName(v.getSourceName())
                .updateTime(v.getUpdateTime())
                .build()).toList();

        return RestResp.ok(newsInfoRespDtoList);
    }

    @Override
    public RestResp<NewsReadRespDto> getNews(Long id) {
        NewsInfo newsInfo = newsInfoMapper.selectById(id);
        QueryWrapper<NewsInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(DatabaseConsts.NewsContentTable.COLUMN_NEWS_ID, id)
                .last(DatabaseConsts.SqlEnum.LIMIT_1.getSql());
        return RestResp.ok(NewsReadRespDto.builder()
                .title(newsInfo.getTitle())
                .sourceName(newsInfo.getSourceName())
                .updateTime(newsInfo.getUpdateTime())
                .content(newsInfo.getContent())
                .build());
    }
}
