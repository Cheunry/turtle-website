package com.novel.book.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.novel.book.dao.entity.BookCategory;
import com.novel.book.dao.entity.BookInfo;
import com.novel.book.dao.mapper.BookCategoryMapper;
import com.novel.book.dao.mapper.BookInfoMapper;
import com.novel.book.dto.resp.BookCategoryRespDto;
import com.novel.book.dto.resp.BookInfoRespDto;
import com.novel.book.dto.resp.BookRankRespDto;
import com.novel.book.service.BookListSearchService;
import com.novel.common.constant.DatabaseConsts;
import com.novel.common.resp.RestResp;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BookListSearchImpl implements BookListSearchService {

    private final BookInfoMapper bookInfoMapper;
    private final BookCategoryMapper bookCategoryMapper;

    @Override
    public RestResp<List<BookRankRespDto>> listVisitRankBooks() {
        QueryWrapper<BookInfo> bookInfoQueryWrapper = new QueryWrapper<>();
        bookInfoQueryWrapper.orderByDesc(DatabaseConsts.BookTable.COLUMN_VISIT_COUNT);
        return RestResp.ok(listRankBooks(bookInfoQueryWrapper));
    }

    @Override
    public RestResp<List<BookRankRespDto>> listNewestRankBooks() {

        QueryWrapper<BookInfo> bookInfoQueryWrapper = new QueryWrapper<>();
        bookInfoQueryWrapper
                .gt(DatabaseConsts.BookTable.COLUMN_WORD_COUNT, 0)
                .orderByDesc(DatabaseConsts.CommonColumnEnum.CREATE_TIME.getName());
        return RestResp.ok(listRankBooks(bookInfoQueryWrapper));
    }

    @Override
    public RestResp<List<BookRankRespDto>> listUpdateRankBooks() {
        QueryWrapper<BookInfo> bookInfoQueryWrapper = new QueryWrapper<>();
        bookInfoQueryWrapper
                .gt(DatabaseConsts.BookTable.COLUMN_WORD_COUNT, 0)
                .orderByDesc(DatabaseConsts.CommonColumnEnum.UPDATE_TIME.getName());
        return RestResp.ok(listRankBooks(bookInfoQueryWrapper));
    }


    @Override
    public RestResp<List<BookCategoryRespDto>> listCategory(Integer workDirection) {
        QueryWrapper<BookCategory> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(DatabaseConsts.BookCategoryTable.COLUMN_WORK_DIRECTION, workDirection);
        List<BookCategoryRespDto> categoryList = bookCategoryMapper.selectList(queryWrapper).stream().map(v->
                BookCategoryRespDto.builder()
                        .id(v.getId())
                        .name(v.getName())
                        .build()
        ).toList();
        return RestResp.ok(categoryList);
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

    /**
     *
     * @param bookInfoQueryWrapper
     * @return 排行榜列表
     */
    private List<BookRankRespDto> listRankBooks(QueryWrapper<BookInfo> bookInfoQueryWrapper) {
        bookInfoQueryWrapper
                .gt(DatabaseConsts.BookTable.COLUMN_WORD_COUNT, 0)
                .last(DatabaseConsts.SqlEnum.LIMIT_30.getSql());
        return bookInfoMapper.selectList(bookInfoQueryWrapper).stream().map(v -> {
            BookRankRespDto respDto = new BookRankRespDto();
            respDto.setId(v.getId());
            respDto.setCategoryId(v.getCategoryId());
            respDto.setCategoryName(v.getCategoryName());
            respDto.setBookName(v.getBookName());
            respDto.setAuthorName(v.getAuthorName());
            respDto.setPicUrl(v.getPicUrl());
            respDto.setBookDesc(v.getBookDesc());
            respDto.setLastChapterName(v.getLastChapterName());
            respDto.setLastChapterUpdateTime(v.getLastChapterUpdateTime());
            respDto.setWordCount(v.getWordCount());
            return respDto;
        }).toList();
    }
}
