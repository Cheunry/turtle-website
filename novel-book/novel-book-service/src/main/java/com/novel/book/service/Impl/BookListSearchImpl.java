package com.novel.book.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.novel.book.dao.entity.BookInfo;
import com.novel.book.dao.mapper.BookInfoMapper;
import com.novel.book.dto.resp.BookCategoryRespDto;
import com.novel.book.dto.resp.BookInfoRespDto;
import com.novel.book.dto.resp.BookRankRespDto;
import com.novel.book.manager.cache.BookCategoryCacheManager;
import com.novel.book.manager.cache.BookRankCacheManager;
import com.novel.book.service.BookListSearchService;
import com.novel.common.resp.RestResp;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BookListSearchImpl implements BookListSearchService {

    private final BookRankCacheManager bookRankCacheManager;
    private final BookCategoryCacheManager bookCategoryCacheManager;
    private final BookInfoMapper bookInfoMapper;

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

    @Override
    public RestResp<List<BookInfoRespDto>> listRecBooks(Long bookId) {
        BookInfo bookInfo = bookInfoMapper.selectById(bookId);
        if (bookInfo == null) {
            return RestResp.ok(Collections.emptyList());
        }
        // 查询同类推荐（访问量最高的4本）
        QueryWrapper<BookInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("category_id", bookInfo.getCategoryId())
                .ne("id", bookId)
                .orderByDesc("visit_count")
                .last("limit 4");
        List<BookInfo> bookInfos = bookInfoMapper.selectList(queryWrapper);
        List<BookInfoRespDto> result = bookInfos.stream().map(b -> BookInfoRespDto.builder()
                .id(b.getId())
                .bookName(b.getBookName())
                .picUrl(b.getPicUrl())
                .bookDesc(b.getBookDesc())
                .authorName(b.getAuthorName())
                .build()).collect(Collectors.toList());
        return RestResp.ok(result);
    }
}
