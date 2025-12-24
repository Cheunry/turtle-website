package com.novel.book.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.novel.book.dao.entity.BookChapter;
import com.novel.book.dao.entity.BookInfo;
import com.novel.book.dao.mapper.BookChapterMapper;
import com.novel.book.dao.mapper.BookInfoMapper;
import com.novel.book.dto.resp.BookChapterAboutRespDto;
import com.novel.book.dto.resp.BookChapterRespDto;
import com.novel.book.dto.resp.BookInfoRespDto;
import com.novel.common.constant.DatabaseConsts;
import com.novel.common.resp.RestResp;
import com.novel.book.service.BookSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

import com.novel.common.constant.AmqpConsts;
import org.apache.rocketmq.spring.core.RocketMQTemplate;

import com.novel.common.constant.CacheConsts;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.util.CollectionUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookSearchServiceImpl implements BookSearchService {

    private final BookInfoMapper bookInfoMapper;
    private final BookChapterMapper bookChapterMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final RocketMQTemplate rocketMQTemplate;

    /**
     * 查询书籍信息
     * @param bookId 小说ID
     * @return 书籍基础信息相应
     */
    @Override
    public RestResp<BookInfoRespDto> getBookById(Long bookId) {
        // 1. 尝试从 Redis Hash 获取书籍详情 (Top 100 热门书)
        Map<Object, Object> bookInfoMap = stringRedisTemplate.opsForHash().entries(CacheConsts.BOOK_INFO_HASH_PREFIX + bookId);
        if (!CollectionUtils.isEmpty(bookInfoMap)) {
            try {
                // 检查关键字段是否存在 (防止缓存不完整)
                if (bookInfoMap.containsKey("bookName") && bookInfoMap.containsKey("firstChapterNum")) {
                    BookInfoRespDto dto = new BookInfoRespDto();
                    dto.setId(bookId);
                    dto.setBookName((String) bookInfoMap.get("bookName"));
                    dto.setBookDesc((String) bookInfoMap.get("bookDesc"));
                    dto.setBookStatus(Integer.parseInt((String) bookInfoMap.get("bookStatus")));
                    dto.setAuthorId(Long.parseLong((String) bookInfoMap.get("authorId")));
                    dto.setAuthorName((String) bookInfoMap.get("authorName"));
                    dto.setCategoryName((String) bookInfoMap.get("categoryName"));
                    dto.setPicUrl((String) bookInfoMap.get("picUrl"));
                    dto.setLastChapterName((String) bookInfoMap.get("lastChapterName"));
                    
                    if (bookInfoMap.containsKey("categoryId")) dto.setCategoryId(Long.parseLong((String) bookInfoMap.get("categoryId")));
                    if (bookInfoMap.containsKey("workDirection")) dto.setWorkDirection(Integer.parseInt((String) bookInfoMap.get("workDirection")));
                    if (bookInfoMap.containsKey("commentCount")) dto.setCommentCount(Integer.parseInt((String) bookInfoMap.get("commentCount")));
                    if (bookInfoMap.containsKey("firstChapterNum")) dto.setFirstChapterNum(Integer.parseInt((String) bookInfoMap.get("firstChapterNum")));
                    if (bookInfoMap.containsKey("lastChapterNum")) dto.setLastChapterNum(Integer.parseInt((String) bookInfoMap.get("lastChapterNum")));
                    if (bookInfoMap.containsKey("visitCount")) dto.setVisitCount(Long.parseLong((String) bookInfoMap.get("visitCount")));
                    if (bookInfoMap.containsKey("wordCount")) dto.setWordCount(Integer.parseInt((String) bookInfoMap.get("wordCount")));
                    
                    String updateTimeStr = (String) bookInfoMap.get("updateTime");
                    if (updateTimeStr != null) {
                        dto.setUpdateTime(LocalDateTime.parse(updateTimeStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                    }
                    
                    log.info(">>> 详情页命中 Redis Hash 缓存，bookId={}", bookId);
                    return RestResp.ok(dto);
                }
            } catch (Exception e) {
                log.error("解析 Redis Hash 详情失败，降级查 DB，bookId={}", bookId, e);
            }
        }

        log.info(">>> 详情页未命中缓存 (或缓存不完整)，回源查询 DB，bookId={}", bookId);

        // 2. 查 DB (原有逻辑)
        BookInfo bookInfo = bookInfoMapper.selectById(bookId);
        
        // 检查书籍审核状态：只有审核通过的书籍才能被读者查看
        if (bookInfo == null || bookInfo.getAuditStatus() == null || bookInfo.getAuditStatus() != 1) {
            // 书籍不存在或未审核通过，返回错误
            return RestResp.fail(com.novel.common.constant.ErrorCodeEnum.USER_REQUEST_PARAM_ERROR);
        }
        
        // 查询首章ID（只查询审核通过的章节）
        QueryWrapper<BookChapter> queryWrapper = new QueryWrapper<>();
        queryWrapper
                .eq(DatabaseConsts.BookChapterTable.COLUMN_BOOK_ID, bookId)
                .eq("audit_status", 1) // 只查询审核通过的章节
                .orderByAsc(DatabaseConsts.BookChapterTable.COLUMN_CHAPTER_NUM) // Asc 正序
                .last(DatabaseConsts.SqlEnum.LIMIT_1.getSql());
        BookChapter firstBookChapter = bookChapterMapper.selectOne(queryWrapper);

        // 组装响应对象
        BookInfoRespDto bookInfoRespDto = BookInfoRespDto.builder()
                .id(bookInfo.getId())
                .bookName(bookInfo.getBookName())
                .bookDesc(bookInfo.getBookDesc())
                .bookStatus(bookInfo.getBookStatus())
                .authorId(bookInfo.getAuthorId())
                .authorName(bookInfo.getAuthorName())
                .categoryId(bookInfo.getCategoryId())
                .categoryName(bookInfo.getCategoryName())
                .workDirection(bookInfo.getWorkDirection()) // Add this line
                .commentCount(bookInfo.getCommentCount())
                .firstChapterNum(firstBookChapter != null ? firstBookChapter.getChapterNum() : 1) // 增加判空处理
                .lastChapterNum(bookInfo.getLastChapterNum())     // 使用 bookInfo 中的数据
                .lastChapterName(bookInfo.getLastChapterName())   // 使用 bookInfo 中的数据
                .picUrl(bookInfo.getPicUrl())
                .visitCount(bookInfo.getVisitCount())
                .wordCount(bookInfo.getWordCount())
                .updateTime(bookInfo.getUpdateTime())
                .build();

        return RestResp.ok(bookInfoRespDto);
    }


    /**
     * 获取书籍列表
     * @param bookIds 小说ID列表
     * @return 书籍基础信息列表
     */
    @Override
    public RestResp<List<BookInfoRespDto>> listBookInfoByIds(List<Long> bookIds) {
        QueryWrapper<BookInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.in(DatabaseConsts.CommonColumnEnum.ID.getName(), bookIds)
                .eq("audit_status", 1); // 只返回审核通过的书籍
        return RestResp.ok(
                bookInfoMapper.selectList(queryWrapper).stream().map(v -> BookInfoRespDto.builder()
                        .id(v.getId())
                        .bookName(v.getBookName())
                        .authorName(v.getAuthorName())
                        .picUrl(v.getPicUrl())
                        .bookDesc(v.getBookDesc())
                        .auditStatus(v.getAuditStatus()) // 包含审核状态
                        .build()).collect(Collectors.toList()));
    }

    @Override
    public RestResp<List<BookInfoRespDto>> listBookInfoByIdsForBookshelf(List<Long> bookIds) {
        // 用于书架查询，不过滤审核状态，返回所有书籍
        QueryWrapper<BookInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.in(DatabaseConsts.CommonColumnEnum.ID.getName(), bookIds);
        return RestResp.ok(
                bookInfoMapper.selectList(queryWrapper).stream().map(v -> {
                    // 查询首章ID（只查询审核通过的章节）
                    QueryWrapper<BookChapter> chapterQueryWrapper = new QueryWrapper<>();
                    chapterQueryWrapper.eq(DatabaseConsts.BookChapterTable.COLUMN_BOOK_ID, v.getId())
                            .eq("audit_status", 1) // 只查询审核通过的章节
                            .orderByAsc(DatabaseConsts.BookChapterTable.COLUMN_CHAPTER_NUM)
                            .last(DatabaseConsts.SqlEnum.LIMIT_1.getSql());
                    BookChapter firstBookChapter = bookChapterMapper.selectOne(chapterQueryWrapper);
                    
                    return BookInfoRespDto.builder()
                            .id(v.getId())
                            .bookName(v.getBookName())
                            .authorName(v.getAuthorName())
                            .picUrl(v.getPicUrl())
                            .bookDesc(v.getBookDesc())
                            .auditStatus(v.getAuditStatus() != null ? v.getAuditStatus() : 0) // 包含审核状态
                            .firstChapterNum(firstBookChapter != null ? firstBookChapter.getChapterNum() : null)
                            .build();
                }).collect(Collectors.toList()));
    }

    /**
     * 增加书籍访问量
     * @param bookId 小说ID
     * @return void
     */
    @Override
    public RestResp<Void> addVisitCount(Long bookId) {
        try {
            // 1. 优先更新 Redis (高性能模式)
            // 更新点击榜 ZSet (实时排名)
            stringRedisTemplate.opsForZSet().incrementScore(CacheConsts.BOOK_VISIT_RANK_ZSET, String.valueOf(bookId), 1);
            // 更新点击量计数器 Hash (用于定时批量刷库)
            stringRedisTemplate.opsForHash().increment(CacheConsts.BOOK_VISIT_COUNT_HASH, String.valueOf(bookId), 1);
        } catch (Exception e) {
            log.error("Redis 异常，降级为直接写数据库，bookId={}", bookId, e);
            // 2. Redis 挂了，降级：直接写库 + 发 MQ (保证数据不丢失)
            bookInfoMapper.addVisitCount(bookId);
            rocketMQTemplate.convertAndSend(AmqpConsts.BookChangeMq.TOPIC + ":" + AmqpConsts.BookChangeMq.TAG_UPDATE, bookId);
        }
        return RestResp.ok();
    }

    /**
     * 获取书籍最新章节信息
     * @param bookId 小说ID
     * @return 书籍章节基础信息
     */
    @Override
    public RestResp<BookChapterAboutRespDto> getLastChapterAbout(Long bookId) {
        // 查询小说信息
//        BookInfoRespDto bookInfo = bookInfoCacheManager.getBookInfo(bookId);


        // 查询最新章节信息（只查询审核通过的章节，auditStatus=1）
        QueryWrapper<BookChapter> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(DatabaseConsts.BookChapterTable.COLUMN_BOOK_ID, bookId)
                .eq("audit_status", 1) // 只查询审核通过的章节
                .orderByDesc(DatabaseConsts.BookChapterTable.COLUMN_CHAPTER_NUM)
                .last(DatabaseConsts.SqlEnum.LIMIT_1.getSql());
        BookChapter latestChapter = bookChapterMapper.selectOne(queryWrapper);

        // 如果章节为空，直接返回空对象
        if (latestChapter == null) {
            return RestResp.ok(BookChapterAboutRespDto.builder()
                    .chapterTotal(0L)
                    .contentSummary("暂无章节")
                    .build());
        }

        // 将 entity 转换为 dto
        BookChapterRespDto bookChapter = BookChapterRespDto.builder()
                .bookId(latestChapter.getBookId())
                .chapterNum(latestChapter.getChapterNum())
                .chapterName(latestChapter.getChapterName())
                .chapterWordCount(latestChapter.getWordCount())
                .chapterUpdateTime(latestChapter.getUpdateTime())
                .isVip(latestChapter.getIsVip())
                .content(latestChapter.getContent())
                .build();

        // 章节内容
        String content = bookChapter.getContent();
        
        // 查询章节总数（只统计审核通过的章节）
        QueryWrapper<BookChapter> chapterQueryWrapper = new QueryWrapper<>();
        chapterQueryWrapper.eq(DatabaseConsts.BookChapterTable.COLUMN_BOOK_ID, bookId)
                .eq("audit_status", 1); // 只统计审核通过的章节
        Long chapterTotal = bookChapterMapper.selectCount(chapterQueryWrapper);

        // 组装数据并返回
        return RestResp.ok(BookChapterAboutRespDto.builder()
                .chapterInfo(bookChapter)
                .chapterTotal(chapterTotal)
                .contentSummary(content != null ? content.substring(0, Math.min(content.length(), 30)) : "暂无内容")
                .build());
    }

}
