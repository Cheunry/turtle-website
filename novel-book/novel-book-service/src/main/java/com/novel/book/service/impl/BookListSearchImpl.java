package com.novel.book.service.impl;

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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.novel.common.constant.CacheConsts;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.CollectionUtils;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookListSearchImpl implements BookListSearchService {

    private final BookInfoMapper bookInfoMapper;
    private final BookCategoryMapper bookCategoryMapper;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 获取最多访问的书籍列表
     * @return 最多访问的书籍列表
     */
    @Override
    public RestResp<List<BookRankRespDto>> listVisitRankBooks() {
        // 1. 从 Redis ZSet 获取点击榜前 30 名 ID
        Set<String> bookIdSet = stringRedisTemplate.opsForZSet().reverseRange(CacheConsts.BOOK_VISIT_RANK_ZSET, 0, 29);

        // 2. 如果 Redis 中没有数据，降级为直接查询数据库
        if (CollectionUtils.isEmpty(bookIdSet)) {
            QueryWrapper<BookInfo> bookInfoQueryWrapper = new QueryWrapper<>();
            bookInfoQueryWrapper.orderByDesc(DatabaseConsts.BookTable.COLUMN_VISIT_COUNT);
            return RestResp.ok(listRankBooks(bookInfoQueryWrapper));
        }

        List<BookRankRespDto> resultList = new ArrayList<>();
        for (String bookIdStr : bookIdSet) {
            Long bookId = Long.valueOf(bookIdStr);
            // 3. 尝试从 Redis Hash 获取书籍详情
            Map<Object, Object> bookInfoMap = stringRedisTemplate.opsForHash().entries(CacheConsts.BOOK_INFO_HASH_PREFIX + bookId);

            if (!CollectionUtils.isEmpty(bookInfoMap)) {
                log.info(">>> 点击榜详情命中 Redis Hash 缓存，bookId={}", bookId);

                // 3.1 缓存命中，组装 DTO
                BookRankRespDto dto = new BookRankRespDto();
                dto.setId(bookId);
                dto.setBookName((String) bookInfoMap.get("bookName"));
                dto.setAuthorName((String) bookInfoMap.get("authorName"));
                dto.setPicUrl((String) bookInfoMap.get("picUrl"));
                dto.setBookDesc((String) bookInfoMap.get("bookDesc"));
                dto.setCategoryName((String) bookInfoMap.get("categoryName"));
                dto.setLastChapterName((String) bookInfoMap.get("lastChapterName"));

                String wordCountStr = (String) bookInfoMap.get("wordCount");
                if (wordCountStr != null) dto.setWordCount(Integer.parseInt(wordCountStr));

                String categoryIdStr = (String) bookInfoMap.get("categoryId");
                if (categoryIdStr != null) dto.setCategoryId(Long.parseLong(categoryIdStr));

                String updateTimeStr = (String) bookInfoMap.get("lastChapterUpdateTime");
                if (updateTimeStr != null) {
                    try {
                        dto.setLastChapterUpdateTime(LocalDateTime.parse(updateTimeStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                    } catch (Exception e) {
                        // ignore parse error
                    }
                }
                resultList.add(dto);
            } else {
                // 3.2 缓存未命中（说明该书不在 Top 50 预热范围内，或者缓存已失效），回源查询 DB
                log.info(">>> 点击榜详情未命中缓存（或不在 Top 50），回源查询 DB，bookId={}", bookId);

                BookInfo bookInfo = bookInfoMapper.selectById(bookId);
                if (bookInfo != null) {
                    BookRankRespDto dto = new BookRankRespDto();
                    dto.setId(bookInfo.getId());
                    dto.setCategoryId(bookInfo.getCategoryId());
                    dto.setCategoryName(bookInfo.getCategoryName());
                    dto.setBookName(bookInfo.getBookName());
                    dto.setAuthorName(bookInfo.getAuthorName());
                    dto.setPicUrl(bookInfo.getPicUrl());
                    dto.setBookDesc(bookInfo.getBookDesc());
                    dto.setLastChapterName(bookInfo.getLastChapterName());
                    dto.setLastChapterUpdateTime(bookInfo.getLastChapterUpdateTime());
                    dto.setWordCount(bookInfo.getWordCount());
                    resultList.add(dto);
                }
            }
        }
        return RestResp.ok(resultList);
    }

    /**
     * 获取最近发布的书籍列表
     * @return 最近发布的书籍列表
     */
    @Override
    public RestResp<List<BookRankRespDto>> listNewestRankBooks() {

        QueryWrapper<BookInfo> bookInfoQueryWrapper = new QueryWrapper<>();
        bookInfoQueryWrapper
                .gt(DatabaseConsts.BookTable.COLUMN_WORD_COUNT, 0)
                .orderByDesc(DatabaseConsts.CommonColumnEnum.CREATE_TIME.getName());
        return RestResp.ok(listRankBooks(bookInfoQueryWrapper));
    }

    /**
     * 获取最近更新的书籍列表
     * @return 最近更新的书籍列表
     */
    @Override
    public RestResp<List<BookRankRespDto>> listUpdateRankBooks() {
        QueryWrapper<BookInfo> bookInfoQueryWrapper = new QueryWrapper<>();
        bookInfoQueryWrapper
                .gt(DatabaseConsts.BookTable.COLUMN_WORD_COUNT, 0)
                .orderByDesc(DatabaseConsts.CommonColumnEnum.UPDATE_TIME.getName());
        return RestResp.ok(listRankBooks(bookInfoQueryWrapper));
    }


    /**
     * 获取根据书籍方向获取这个方向的书籍类型列表
     * @param workDirection 作品方向;0-男频 1-女频
     * @return 书籍类型信息列表
     */
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

    /**
     * 推荐同类书籍列表
     * @param bookId 小说ID
     * @return 同列书籍列表
     */
    @Override
    public RestResp<List<BookInfoRespDto>> listRecBooks(Long bookId) {

        BookInfo bookInfo = bookInfoMapper.selectById(bookId);
        if (bookInfo == null) {
            return RestResp.ok(Collections.emptyList());
        }
        // 查询同类推荐（访问量最高的4本，只查询审核通过的书籍）
        QueryWrapper<BookInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("category_id", bookInfo.getCategoryId())
                .eq("audit_status", 1) // 只查询审核通过的书籍
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
     * 书籍榜单列表
     * @param bookInfoQueryWrapper 数据库获取的书籍榜单列表
     * @return 排行榜列表
     */
    private List<BookRankRespDto> listRankBooks(QueryWrapper<BookInfo> bookInfoQueryWrapper) {
        bookInfoQueryWrapper
                .gt(DatabaseConsts.BookTable.COLUMN_WORD_COUNT, 0)
                .eq("audit_status", 1) // 只查询审核通过的书籍
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
