package com.novel.book.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.novel.book.dao.entity.BookCategory;
import com.novel.book.dao.entity.BookChapter;
import com.novel.book.dao.entity.BookInfo;
import com.novel.book.dao.entity.HomeBook;
import com.novel.book.dao.mapper.BookCategoryMapper;
import com.novel.book.dao.mapper.BookChapterMapper;
import com.novel.book.dao.mapper.BookInfoMapper;
import com.novel.book.dao.mapper.HomeBookMapper;
import com.novel.book.dto.resp.*;
import com.novel.common.constant.DatabaseConsts;
import com.novel.common.resp.RestResp;
import com.novel.book.service.BookSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.novel.common.constant.AmqpConsts;
import org.apache.rocketmq.spring.core.RocketMQTemplate;

import com.novel.common.constant.CacheConsts;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.cache.annotation.Cacheable;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookSearchServiceImpl implements BookSearchService {

    private final BookInfoMapper bookInfoMapper;
    private final BookChapterMapper bookChapterMapper;
    private final HomeBookMapper homeBookMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final RocketMQTemplate rocketMQTemplate;

    private final BookCategoryMapper bookCategoryMapper;

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
            QueryWrapper<BookInfo> bookQueryWrapper = new QueryWrapper<>();
            bookQueryWrapper.in(DatabaseConsts.CommonColumnEnum.ID.getName(), bookIds)
                    .eq("audit_status", 1); // 只返回审核通过的书籍
            List<BookInfo> bookInfos = bookInfoMapper.selectList(bookQueryWrapper);

            // 组装 HomeBookRespDto 列表数据并返回（只显示审核通过的书籍，auditStatus=1）
            if(!CollectionUtils.isEmpty(bookInfos)) {
                Map<Long, BookInfo> bookInfoMap = bookInfos.stream()
                        .collect(Collectors.toMap(BookInfo::getId, Function.identity()));
                return RestResp.ok(homeBooks.stream()
                        .filter(v -> bookInfoMap.containsKey(v.getBookId())) // 只保留审核通过的书籍
                        .map(v -> {
                            BookInfo bookInfo = bookInfoMap.get(v.getBookId());
                            HomeBookRespDto homeBookRespDto = new HomeBookRespDto();
                            homeBookRespDto.setType(v.getType());
                            homeBookRespDto.setBookId(v.getBookId());
                            homeBookRespDto.setBookName(bookInfo.getBookName());
                            homeBookRespDto.setPicUrl(bookInfo.getPicUrl());
                            homeBookRespDto.setAuthorName(bookInfo.getAuthorName());
                            homeBookRespDto.setBookDesc(bookInfo.getBookDesc());
                            return homeBookRespDto;
                        }).toList());
            }
        }
        return RestResp.ok(Collections.emptyList());
    }


    /**
     * 查询下一批保存到 ES 中的小说列表
     * @param maxBookId 已查询的最大小说ID
     * @return 小说列表
     */
    @Override
    public RestResp<List<BookEsRespDto>> listNextEsBooks(Long maxBookId) {

        // 如果 maxBookId 为 null 或者小于等于 0，则从第一条记录开始拉取
        if (maxBookId == null || maxBookId <= 0) {
            maxBookId = 0L; // 确保第一次查询时，ID大于0的记录都能被查到
        }

        QueryWrapper<BookInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.clear();
        /*
        queryWrapper.怎么查询呢？
            按 ID 升序排列
            查询 ID 大于 maxBookId 的记录（实现“下一批”）
            确保小说 字数大于 0（wordCount > 0）
            限制查询数量（例如 30 条）
         */
        queryWrapper.orderByAsc(DatabaseConsts.CommonColumnEnum.ID.getName())
                .gt(DatabaseConsts.CommonColumnEnum.ID.getName(), maxBookId)
                .gt(DatabaseConsts.BookTable.COLUMN_WORD_COUNT,0)
                .eq("audit_status", 1) // 只索引审核通过的书籍
                .last(DatabaseConsts.SqlEnum.LIMIT_30.getSql());

        return RestResp.ok(
                bookInfoMapper.selectList(queryWrapper).stream()
                        .map(this::convertToBookEsRespDto)
                        .toList()
        );

    }

    /**
     * 根据 ID 获取 ES 书籍数据
     * @param bookId 书籍ID
     * @return Elasticsearch 存储小说 DTO
     */
    @Override
    public RestResp<BookEsRespDto> getEsBookById(Long bookId) {

        BookInfo bookInfo = bookInfoMapper.selectById(bookId);
        // 只有审核通过的书籍才能被索引到ES
        if (bookInfo == null) {
            log.warn(">>> [ES] 书籍不存在，bookId={}", bookId);
            return RestResp.ok(null);
        }
        
        if (bookInfo.getAuditStatus() == null || bookInfo.getAuditStatus() != 1) {
            log.warn(">>> [ES] 书籍未审核通过，不返回ES数据，bookId={}, auditStatus={}, bookName={}", 
                    bookId, bookInfo.getAuditStatus(), bookInfo.getBookName());
            return RestResp.ok(null);
        }

        log.debug(">>> [ES] 获取书籍ES数据成功，bookId={}, bookName={}", bookId, bookInfo.getBookName());
        return RestResp.ok(convertToBookEsRespDto(bookInfo));
    }


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
                BookRankRespDto dto = convertMapToBookRankRespDto(bookId, bookInfoMap);
                if (dto != null) {
                    resultList.add(dto);
                }
            } else {
                // 3.2 缓存未命中（说明该书不在 Top 50 预热范围内，或者缓存已失效），回源查询 DB
                log.info(">>> 点击榜详情未命中缓存（或不在 Top 50），回源查询 DB，bookId={}", bookId);
                BookInfo bookInfo = bookInfoMapper.selectById(bookId);
                if (bookInfo != null) {
                    resultList.add(convertToBookRankRespDto(bookInfo));
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
     * @param workDirection 作品方向;0-男频 1-女频，null 表示查询所有分类
     * @return 书籍类型信息列表
     */
    @Override
    @Cacheable(value = CacheConsts.BOOK_CATEGORY_LIST_CACHE_NAME, 
               cacheManager = CacheConsts.REDIS_CACHE_MANAGER_TYPED,
               key = "#workDirection == null ? 'all' : #workDirection.toString()",
               unless = "#result == null || #result.data == null || #result.data.isEmpty()")
    public RestResp<List<BookCategoryRespDto>> listCategory(Integer workDirection) {
        QueryWrapper<BookCategory> queryWrapper = new QueryWrapper<>();
        // workDirection 为 null 时，返回所有分类
        if (workDirection != null) {
            queryWrapper.eq(DatabaseConsts.BookCategoryTable.COLUMN_WORK_DIRECTION, workDirection);
        }
        queryWrapper.orderByAsc(DatabaseConsts.BookCategoryTable.COLUMN_SORT);
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
        return bookInfoMapper.selectList(bookInfoQueryWrapper).stream()
                .map(this::convertToBookRankRespDto)
                .toList();
    }

    /**
     * 将 BookInfo 转换为 BookRankRespDto
     * @param bookInfo 书籍信息实体
     * @return 排行榜 DTO
     */
    private BookRankRespDto convertToBookRankRespDto(BookInfo bookInfo) {
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
        return dto;
    }

    /**
     * 将 Redis Hash Map 转换为 BookRankRespDto
     * @param bookId 书籍ID
     * @param bookInfoMap Redis Hash Map
     * @return 排行榜 DTO，如果转换失败返回 null
     */
    private BookRankRespDto convertMapToBookRankRespDto(Long bookId, Map<Object, Object> bookInfoMap) {
        try {
            BookRankRespDto dto = new BookRankRespDto();
            dto.setId(bookId);
            dto.setBookName((String) bookInfoMap.get("bookName"));
            dto.setAuthorName((String) bookInfoMap.get("authorName"));
            dto.setPicUrl((String) bookInfoMap.get("picUrl"));
            dto.setBookDesc((String) bookInfoMap.get("bookDesc"));
            dto.setCategoryName((String) bookInfoMap.get("categoryName"));
            dto.setLastChapterName((String) bookInfoMap.get("lastChapterName"));

            String wordCountStr = (String) bookInfoMap.get("wordCount");
            if (wordCountStr != null) {
                dto.setWordCount(Integer.parseInt(wordCountStr));
            }

            String categoryIdStr = (String) bookInfoMap.get("categoryId");
            if (categoryIdStr != null) {
                dto.setCategoryId(Long.parseLong(categoryIdStr));
            }

            String updateTimeStr = (String) bookInfoMap.get("lastChapterUpdateTime");
            if (updateTimeStr != null) {
                try {
                    dto.setLastChapterUpdateTime(LocalDateTime.parse(updateTimeStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                } catch (Exception e) {
                    log.warn("解析更新时间失败，bookId={}, updateTimeStr={}", bookId, updateTimeStr);
                }
            }
            return dto;
        } catch (Exception e) {
            log.error("转换 Redis Hash Map 为 BookRankRespDto 失败，bookId={}", bookId, e);
            return null;
        }
    }

    /**
     * 将 BookInfo 转换为 BookEsRespDto
     * @param bookInfo 书籍信息实体
     * @return ES 书籍 DTO
     */
    private BookEsRespDto convertToBookEsRespDto(BookInfo bookInfo) {
        return BookEsRespDto.builder()
                .id(bookInfo.getId())
                .workDirection(bookInfo.getWorkDirection())
                .categoryId(bookInfo.getCategoryId())
                .categoryName(bookInfo.getCategoryName())
                .bookName(bookInfo.getBookName())
                .picUrl(bookInfo.getPicUrl())
                .authorName(bookInfo.getAuthorName())
                .bookDesc(bookInfo.getBookDesc())
                .score(bookInfo.getScore())
                .bookStatus(bookInfo.getBookStatus())
                .visitCount(bookInfo.getVisitCount())
                .wordCount(bookInfo.getWordCount())
                .commentCount(bookInfo.getCommentCount())
                .lastChapterName(bookInfo.getLastChapterName())
                // 将数据库中的时间转换为一个标准的 Unix 时间戳（毫秒数），这是一个 Long 类型。
                // 增加判空处理，防止 NPE
                .lastChapterUpdateTime(bookInfo.getLastChapterUpdateTime() != null
                        ? bookInfo.getLastChapterUpdateTime().toInstant(ZoneOffset.ofHours(8)).toEpochMilli()
                        : 0L)
                .isVip(bookInfo.getIsVip())
                .build();
    }

}
