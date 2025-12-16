package com.novel.home.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.novel.book.dto.resp.BookInfoRespDto;
import com.novel.common.constant.DatabaseConsts;
import com.novel.common.resp.RestResp;
import com.novel.home.dao.entity.HomeBook;
import com.novel.home.dao.entity.HomeFriendLink;
import com.novel.home.dao.mapper.HomeBookMapper;
import com.novel.home.dao.mapper.HomeFriendLinkMapper;
import com.novel.home.dto.resp.HomeBookRespDto;
import com.novel.home.dto.resp.HomeFriendLinkRespDto;
import com.novel.home.feign.HomeBookFeignManager;
import com.novel.home.service.HomeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 首页模块-服务实现类
 */
@Service
@RequiredArgsConstructor
public class HomeServiceImpl implements HomeService {

    private final HomeBookMapper homeBookMapper;
    private final HomeBookFeignManager homeBookFeignManager;
    private final HomeFriendLinkMapper friendLinkMapper;

    /**
     * 查询首页小说展示列表
     * @return 首页小说展示列表的rest响应结果
     */
    @Override
    public RestResp<List<HomeBookRespDto>> listHomeBook() {

        // 从首页小说展示表中查询出需要展示的小说
        QueryWrapper<HomeBook> queryWrapper = new QueryWrapper<>();
        queryWrapper.orderByDesc(DatabaseConsts.CommonColumnEnum.SORT.getName());
        List<HomeBook> homeBooks = homeBookMapper.selectList(queryWrapper);

        // 获取首页小说展示列表书籍的id
        if(!CollectionUtils.isEmpty(homeBooks)) {
            List<Long> bookIds = homeBooks.stream()
                    .map(HomeBook::getBookId)
                    .toList();

            // 根据小说ID列表查询相关的小说信息列表
            List<BookInfoRespDto> bookInfoRespDtos = homeBookFeignManager.listBookInfoById(bookIds);

            // 组装 HomeBookRespDto 列表数据并返回
            if(!CollectionUtils.isEmpty(bookInfoRespDtos)) {
                Map<Long, BookInfoRespDto> bookInfoRespDtoMap = bookInfoRespDtos.stream()
                        .collect(Collectors.toMap(BookInfoRespDto::getId, Function.identity()));
                return RestResp.ok(homeBooks.stream().map(v -> {
                    BookInfoRespDto bookInfoRespDto = bookInfoRespDtoMap.get(v.getBookId());
                    HomeBookRespDto homeBookRespDto = new HomeBookRespDto();
                    homeBookRespDto.setType(v.getType());
                    homeBookRespDto.setBookId(v.getBookId());
                    homeBookRespDto.setBookName(bookInfoRespDto.getBookName());
                    homeBookRespDto.setPicUrl(bookInfoRespDto.getPicUrl());
                    homeBookRespDto.setAuthorName(bookInfoRespDto.getAuthorName());
                    homeBookRespDto.setBookDesc(bookInfoRespDto.getBookDesc());
                    return homeBookRespDto;
                }).toList());
            }
        }
        return RestResp.ok(Collections.emptyList());
    }

    /**
     * 查询首页友情链接列表
     * @return 首页友情链接列表的rest响应结果
     */
    @Override
    public RestResp<List<HomeFriendLinkRespDto>> listHomeFriendLink() {

        // 从友情链接表中查询出友情链接列表
        QueryWrapper<HomeFriendLink> queryWrapper = new QueryWrapper<>();
        queryWrapper.orderByDesc(DatabaseConsts.CommonColumnEnum.SORT.getName());

        return RestResp.ok(
                friendLinkMapper.selectList(queryWrapper).stream().map(v -> {
                    HomeFriendLinkRespDto dto = new HomeFriendLinkRespDto();
                    dto.setLinkName(v.getLinkName());
                    dto.setLinkUrl(v.getLinkUrl());
                    return dto;
                }).toList()
        );
    }
}
