package com.novel.book.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.novel.book.dao.entity.BookChapter;
import com.novel.book.dao.mapper.BookChapterMapper;
import com.novel.book.dto.resp.BookCategoryRespDto;
import com.novel.book.dto.resp.BookChapterRespDto;
import com.novel.book.dto.resp.BookRankRespDto;
import com.novel.book.manager.cache.BookCategoryCacheManager;
import com.novel.book.manager.cache.BookRankCacheManager;
import com.novel.book.service.BookListSearchService;
import com.novel.common.constant.DatabaseConsts;
import com.novel.common.resp.RestResp;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BookListSearchImpl implements BookListSearchService {

    private final BookRankCacheManager bookRankCacheManager;
    private final BookChapterMapper bookChapterMapper;
    private final BookCategoryCacheManager bookCategoryCacheManager;

    @Override
    public RestResp<List<BookRankRespDto>> listVisitRankBooks() {
        return RestResp.ok(bookRankCacheManager.listVisitRankBooks());
    }

    @Override
    public RestResp<List<BookRankRespDto>> listNewestRankBooks() {
        return RestResp.ok(bookRankCacheManager.listNewestRankBooks());
    }

    @Override
    public RestResp<List<BookRankRespDto>> listUpdateRankBooks() {
        return RestResp.ok(bookRankCacheManager.listUpdateRankBooks());
    }


    @Override
    public RestResp<List<BookCategoryRespDto>> listCategory(Integer workDirection) {
        return RestResp.ok(bookCategoryCacheManager.listCategory(workDirection));
    }
}
