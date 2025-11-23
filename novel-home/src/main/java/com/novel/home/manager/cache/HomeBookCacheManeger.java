package com.novel.home.manager.cache;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.novel.book.dto.resp.BookInfoRespDto;
import com.novel.common.constant.CacheConsts;
import com.novel.common.constant.DatabaseConsts;
import com.novel.home.dao.entity.HomeBook;
import com.novel.home.dao.mapper.HomeBookMapper;
import com.novel.home.dto.resp.HomeBookRespDto;
import com.novel.home.manager.feign.HomeBookFeignManager;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 首页展示小说-缓存管理类
 */
@Component
@RequiredArgsConstructor
public class HomeBookCacheManeger {
    private final HomeBookMapper homeBookMapper;
    private final HomeBookFeignManager homeBookFeignManager;

    /**
     *  查询首页展示小说，并且放入缓存
     */
    @Cacheable(cacheManager = CacheConsts.REDIS_CACHE_MANAGER_PLAIN, value = CacheConsts.HOME_BOOK_CACHE_NAME)
    public List<HomeBookRespDto> listHomeBooks() {
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
                return homeBooks.stream().map(v -> {
                    BookInfoRespDto bookInfoRespDto = bookInfoRespDtoMap.get(v.getBookId());
                    HomeBookRespDto homeBookRespDto = new HomeBookRespDto();
                    homeBookRespDto.setType(v.getType());
                    homeBookRespDto.setBookId(v.getBookId());
                    homeBookRespDto.setBookName(bookInfoRespDto.getBookName());
                    homeBookRespDto.setPicUrl(bookInfoRespDto.getPicUrl());
                    homeBookRespDto.setAuthorName(bookInfoRespDto.getAuthorName());
                    homeBookRespDto.setBookDesc(bookInfoRespDto.getBookDesc());
                    return homeBookRespDto;
                }).toList();
            }
        }
        return Collections.emptyList();
    }

    @CacheEvict(cacheManager = CacheConsts.REDIS_CACHE_MANAGER_PLAIN,
            value = CacheConsts.HOME_BOOK_CACHE_NAME)
    public void evictCache() {}
    /*
        空方法 + @CacheEvict 是官方推荐的“手动清缓存”写法
        @CacheEvict 是干什么的？
            把指定缓存（这里叫 homeBook）全部清空
            （默认 key 是 CacheConsts.HOME_BOOK_CACHE_NAME 下所有条目）。

     */



}

/*
    @Component
        把类标记成 Spring 容器里的 Bean，让 Spring 扫描到时自动实例化、管理生命周期，其他地方才能 @Autowired 进来用。
    @RequiredArgsConstructor
        Lombok 注解，给所有 final 成员变量自动生成一个构造器；
        Spring 启动时用这构造器完成依赖注入（构造器注入），省掉手写 public Xxx(YYY yyy) { this.yyy = yyy; }。
    总之，
        @Component 让类进容器，
        @RequiredArgsConstructor 让 Lombok 帮你生成带参构造器，Spring 通过这个构造器把依赖一次性注入，代码简洁又安全。
 */