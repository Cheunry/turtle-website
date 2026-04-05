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
import com.novel.common.util.RankBookDescUtils;
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
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.core.io.ClassPathResource;

import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;
import java.util.Collections;

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

    // 手动注入 ObjectMapper 并注册 JavaTimeModule
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // Lua 脚本：原子性地增加访问量
    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> INCREMENT_VISIT_COUNT_SCRIPT;

    static {
        INCREMENT_VISIT_COUNT_SCRIPT = new DefaultRedisScript<>();
        INCREMENT_VISIT_COUNT_SCRIPT.setLocation(new ClassPathResource("lua/incrementVisitCount.lua"));
        INCREMENT_VISIT_COUNT_SCRIPT.setResultType(List.class);
    }

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
                        dto.setUpdateTime(LocalDateTime.parse(updateTimeStr, DATE_TIME_FORMATTER));
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

        // --- 开始回写 Redis (使用 Pipeline 减少 RTT) ---
        // 注意：只缓存榜单中的热门书籍，其他书籍不缓存（避免Redis内存占用过大）
        // 判断是否在榜单中：检查ZSet中是否存在该书籍ID
        try {
            Double zsetScore = stringRedisTemplate.opsForZSet().score(CacheConsts.BOOK_VISIT_RANK_ZSET, String.valueOf(bookId));
            boolean isInRank = (zsetScore != null && zsetScore > 0);
            
            if (isInRank) {
                // 在榜单中，写入缓存（1小时过期）
                Map<String, String> cacheMap = buildBookInfoMap(bookInfo, firstBookChapter != null ? firstBookChapter.getChapterNum() : 1);
                String redisKey = CacheConsts.BOOK_INFO_HASH_PREFIX + bookId;
                
                stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                    RedisSerializer<String> serializer = stringRedisTemplate.getStringSerializer();
                    byte[] keyBytes = serializer.serialize(redisKey);
                    
                    // 1. HMSET 批量设置字段（使用新的命令接口）
                    Map<byte[], byte[]> byteMap = new HashMap<>();
                    cacheMap.forEach((k, v) -> byteMap.put(serializer.serialize(k), serializer.serialize(v)));
                    connection.hashCommands().hMSet(keyBytes, byteMap);
                    
                    // 2. EXPIRE 设置过期时间 (1小时 = 3600秒)（使用新的命令接口）
                    connection.keyCommands().expire(keyBytes, 3600);
                    
                    return null;
                });
                
                log.info(">>> 详情页回写 Redis Hash 成功 (Pipeline), bookId={} (榜单书籍)", bookId);
            } else {
                // 不在榜单中，不缓存（避免Redis内存占用过大）
                log.debug(">>> 书籍不在榜单中，不缓存到Redis，bookId={}", bookId);
            }
        } catch (Exception e) {
            log.error(">>> 详情页回写 Redis 失败，bookId={}", bookId, e);
        }
        // --- 结束回写 Redis ---

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
            // 使用 Lua 脚本原子性地增加访问量（保证 ZSet、Hash 和书籍详情缓存的原子性）
            String bookIdStr = String.valueOf(bookId);
            List<String> keys = Arrays.asList(
                    CacheConsts.BOOK_VISIT_RANK_ZSET,
                    CacheConsts.BOOK_VISIT_COUNT_HASH,
                    CacheConsts.BOOK_INFO_HASH_PREFIX + bookId  // 书籍详情 Hash key
            );
            @SuppressWarnings("unchecked")
            List<Long> result = stringRedisTemplate.execute(
                    INCREMENT_VISIT_COUNT_SCRIPT,
                    keys,
                    bookIdStr,
                    "1"
            );
            
            if (result != null && !result.isEmpty()) {
                log.debug(">>> 访问量更新成功（Lua脚本原子操作）, bookId={}, zsetScore={}, hashValue={}, 已同步更新书籍详情Hash缓存", 
                        bookId, result.get(0), result.size() > 1 ? result.get(1) : "N/A");
            } else {
                log.warn(">>> Lua脚本执行返回null或空, bookId={}", bookId);
            }
        } catch (Exception e) {
            log.error("Redis 写入异常，进入异步补偿模式, bookId={}", bookId, e);
            // 2. 降级逻辑：仅发送 MQ，让消费端慢慢消化，保护数据库
            // 注意：这里需要考虑 MQ 如果也挂了的极端情况（可记录本地 Error Log）
            sendUpdateMq(bookId);
        }
        return RestResp.ok();
    }

    private void sendUpdateMq(Long bookId) {
        try {
            rocketMQTemplate.convertAndSend(AmqpConsts.BookChangeMq.TOPIC + ":" + AmqpConsts.BookChangeMq.TAG_UPDATE, bookId);
        } catch (Exception ex) {
            log.error("MQ 发送失败，需要人工介入或本地日志补单, bookId={}", bookId);
        }
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
        // 1. 尝试从 Redis 获取缓存
        String cacheKey = CacheConsts.HOME_BOOK_CACHE_NAME;
        String cacheValue = stringRedisTemplate.opsForValue().get(cacheKey);
        
        if (cacheValue != null) {
            try {
                List<HomeBookRespDto> list = objectMapper.readValue(cacheValue, new TypeReference<List<HomeBookRespDto>>() {});
                log.info(">>> 首页推荐命中 Redis 缓存");
                return RestResp.ok(list);
            } catch (Exception e) {
                log.warn("解析首页推荐缓存失败，key={}", cacheKey, e);
            }
        }

        log.info(">>> 首页推荐未命中缓存，回源查询 DB");

        // 2. 从首页小说展示表中查询出需要展示的小说
        QueryWrapper<HomeBook> queryWrapper = new QueryWrapper<>();
        queryWrapper.orderByDesc(DatabaseConsts.CommonColumnEnum.SORT.getName());
        List<HomeBook> homeBooks = homeBookMapper.selectList(queryWrapper);

        List<HomeBookRespDto> respList = Collections.emptyList();

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
                respList = homeBooks.stream()
                        .filter(v -> bookInfoMap.containsKey(v.getBookId())) // 只保留审核通过的书籍
                        .map(v -> {
                            BookInfo bookInfo = bookInfoMap.get(v.getBookId());
                            HomeBookRespDto homeBookRespDto = new HomeBookRespDto();
                            homeBookRespDto.setType(v.getType());
                            homeBookRespDto.setBookId(v.getBookId());
                            homeBookRespDto.setBookName(bookInfo.getBookName());
                            homeBookRespDto.setPicUrl(bookInfo.getPicUrl());
                            homeBookRespDto.setAuthorName(bookInfo.getAuthorName());
                            homeBookRespDto.setBookDesc(RankBookDescUtils.toRankPreview(bookInfo.getBookDesc()));
                            return homeBookRespDto;
                        }).toList();
            }
        }
        
        // 3. 写入缓存 (24小时)
        try {
            if (!CollectionUtils.isEmpty(respList)) {
                String json = objectMapper.writeValueAsString(respList);
                stringRedisTemplate.opsForValue().set(cacheKey, json, 24, TimeUnit.HOURS);
                log.info(">>> 首页推荐已写入 Redis");
            }
        } catch (Exception e) {
            log.error("写入首页推荐缓存失败", e);
        }
        
        return RestResp.ok(respList);
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


    private static final int RANK_PAGE_LIMIT = 30;
    private static final int RANK_HOME_LIMIT = 10;

    @Override
    public RestResp<List<BookRankTableRespDto>> listVisitRankBooks() {
        Set<String> bookIdSet = stringRedisTemplate.opsForZSet().reverseRange(
                CacheConsts.BOOK_VISIT_RANK_ZSET, 0, RANK_PAGE_LIMIT - 1);
        if (CollectionUtils.isEmpty(bookIdSet)) {
            QueryWrapper<BookInfo> qw = new QueryWrapper<>();
            qw.orderByDesc(DatabaseConsts.BookTable.COLUMN_VISIT_COUNT);
            return RestResp.ok(listRankTableFromDb(qw, RANK_PAGE_LIMIT));
        }
        return RestResp.ok(loadVisitRankTableFromRedis(new ArrayList<>(bookIdSet)));
    }

    @Override
    public RestResp<List<BookRankHomeItemRespDto>> listVisitRankBooksHome() {
        Set<String> bookIdSet = stringRedisTemplate.opsForZSet().reverseRange(
                CacheConsts.BOOK_VISIT_RANK_ZSET, 0, RANK_HOME_LIMIT - 1);
        if (CollectionUtils.isEmpty(bookIdSet)) {
            QueryWrapper<BookInfo> qw = new QueryWrapper<>();
            qw.orderByDesc(DatabaseConsts.BookTable.COLUMN_VISIT_COUNT);
            return RestResp.ok(listRankHomeFromDb(qw, RANK_HOME_LIMIT));
        }
        return RestResp.ok(loadVisitRankHomeFromRedis(new ArrayList<>(bookIdSet)));
    }

    @Override
    public RestResp<List<BookRankTableRespDto>> listNewestRankBooks() {
        QueryWrapper<BookInfo> qw = new QueryWrapper<>();
        qw.gt(DatabaseConsts.BookTable.COLUMN_WORD_COUNT, 0)
                .orderByDesc(DatabaseConsts.CommonColumnEnum.CREATE_TIME.getName());
        return RestResp.ok(listRankTableFromDb(qw, RANK_PAGE_LIMIT));
    }

    @Override
    public RestResp<List<BookRankHomeItemRespDto>> listNewestRankBooksHome() {
        QueryWrapper<BookInfo> qw = new QueryWrapper<>();
        qw.gt(DatabaseConsts.BookTable.COLUMN_WORD_COUNT, 0)
                .orderByDesc(DatabaseConsts.CommonColumnEnum.CREATE_TIME.getName());
        return RestResp.ok(listRankHomeFromDb(qw, RANK_HOME_LIMIT));
    }

    @Override
    public RestResp<List<BookRankTableRespDto>> listUpdateRankBooks() {
        QueryWrapper<BookInfo> qw = new QueryWrapper<>();
        qw.gt(DatabaseConsts.BookTable.COLUMN_WORD_COUNT, 0)
                .orderByDesc(DatabaseConsts.CommonColumnEnum.UPDATE_TIME.getName());
        return RestResp.ok(listRankTableFromDb(qw, RANK_PAGE_LIMIT));
    }

    @Override
    public RestResp<List<BookRankHomeItemRespDto>> listUpdateRankBooksHome() {
        QueryWrapper<BookInfo> qw = new QueryWrapper<>();
        qw.gt(DatabaseConsts.BookTable.COLUMN_WORD_COUNT, 0)
                .orderByDesc(DatabaseConsts.CommonColumnEnum.UPDATE_TIME.getName());
        return RestResp.ok(listRankHomeFromDb(qw, RANK_HOME_LIMIT));
    }

    @Override
    public RestResp<List<BookHomeLatestUpdateRespDto>> listHomeLatestUpdates() {
        QueryWrapper<BookInfo> qw = new QueryWrapper<>();
        qw.gt(DatabaseConsts.BookTable.COLUMN_WORD_COUNT, 0)
                .eq("audit_status", 1)
                .orderByDesc(DatabaseConsts.CommonColumnEnum.UPDATE_TIME.getName())
                .last(DatabaseConsts.SqlEnum.LIMIT_30.getSql());
        List<BookInfo> rows = bookInfoMapper.selectList(qw);
        return RestResp.ok(rows.stream().map(this::toHomeLatestUpdateDto).toList());
    }

    private BookHomeLatestUpdateRespDto toHomeLatestUpdateDto(BookInfo b) {
        BookHomeLatestUpdateRespDto dto = new BookHomeLatestUpdateRespDto();
        dto.setId(b.getId());
        dto.setCategoryName(b.getCategoryName());
        dto.setBookName(b.getBookName());
        dto.setLastChapterName(b.getLastChapterName());
        dto.setAuthorName(b.getAuthorName());
        dto.setLastChapterUpdateTime(b.getLastChapterUpdateTime());
        return dto;
    }

    private List<BookRankTableRespDto> listRankTableFromDb(QueryWrapper<BookInfo> qw, int limit) {
        qw.gt(DatabaseConsts.BookTable.COLUMN_WORD_COUNT, 0)
                .eq("audit_status", 1)
                .last("limit " + limit);
        List<BookInfo> rows = bookInfoMapper.selectList(qw);
        List<BookRankTableRespDto> out = new ArrayList<>();
        int rank = 1;
        for (BookInfo b : rows) {
            out.add(toRankTableDto(b, rank++));
        }
        return out;
    }

    private List<BookRankHomeItemRespDto> listRankHomeFromDb(QueryWrapper<BookInfo> qw, int limit) {
        qw.gt(DatabaseConsts.BookTable.COLUMN_WORD_COUNT, 0)
                .eq("audit_status", 1)
                .last("limit " + limit);
        List<BookInfo> rows = bookInfoMapper.selectList(qw);
        return mapDbBooksToHomeRankItems(rows);
    }

    private List<BookRankHomeItemRespDto> mapDbBooksToHomeRankItems(List<BookInfo> rows) {
        List<BookRankHomeItemRespDto> out = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            BookInfo b = rows.get(i);
            BookRankHomeItemRespDto dto = new BookRankHomeItemRespDto();
            dto.setRank(i + 1);
            dto.setId(b.getId());
            dto.setBookName(b.getBookName());
            if (i == 0) {
                dto.setPicUrl(b.getPicUrl());
                dto.setBookDesc(RankBookDescUtils.toRankPreview(b.getBookDesc()));
            }
            out.add(dto);
        }
        return out;
    }

    private BookRankTableRespDto toRankTableDto(BookInfo b, int rank) {
        BookRankTableRespDto dto = new BookRankTableRespDto();
        dto.setRank(rank);
        dto.setId(b.getId());
        dto.setCategoryName(b.getCategoryName());
        dto.setBookName(b.getBookName());
        dto.setLastChapterName(b.getLastChapterName());
        dto.setAuthorName(b.getAuthorName());
        dto.setWordCount(b.getWordCount());
        return dto;
    }

    private List<BookRankTableRespDto> loadVisitRankTableFromRedis(List<String> bookIdStrList) {
        List<Object> pipelineResults = stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            RedisSerializer<String> ser = stringRedisTemplate.getStringSerializer();
            for (String bookIdStr : bookIdStrList) {
                byte[] key = ser.serialize(CacheConsts.BOOK_INFO_HASH_PREFIX + bookIdStr);
                connection.hashCommands().hMGet(key,
                        ser.serialize("categoryName"),
                        ser.serialize("bookName"),
                        ser.serialize("lastChapterName"),
                        ser.serialize("authorName"),
                        ser.serialize("wordCount"));
            }
            return null;
        });
        List<BookRankTableRespDto> out = new ArrayList<>();
        for (int i = 0; i < bookIdStrList.size(); i++) {
            long bookId = Long.parseLong(bookIdStrList.get(i));
            int rank = i + 1;
            @SuppressWarnings("unchecked")
            List<Object> vals = (List<Object>) pipelineResults.get(i);
            BookRankTableRespDto row = parseTableRowFromHmget(bookId, rank, vals);
            if (row != null && row.getBookName() != null) {
                out.add(row);
            } else {
                BookInfo b = bookInfoMapper.selectById(bookId);
                if (b != null) {
                    out.add(toRankTableDto(b, rank));
                }
            }
        }
        return out;
    }

    private List<BookRankHomeItemRespDto> loadVisitRankHomeFromRedis(List<String> bookIdStrList) {
        List<Object> pipelineResults = stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            RedisSerializer<String> ser = stringRedisTemplate.getStringSerializer();
            for (int i = 0; i < bookIdStrList.size(); i++) {
                String bookIdStr = bookIdStrList.get(i);
                byte[] key = ser.serialize(CacheConsts.BOOK_INFO_HASH_PREFIX + bookIdStr);
                if (i == 0) {
                    connection.hashCommands().hMGet(key,
                            ser.serialize("bookName"),
                            ser.serialize("picUrl"),
                            ser.serialize("bookDesc"));
                } else {
                    connection.hashCommands().hMGet(key, ser.serialize("bookName"));
                }
            }
            return null;
        });
        List<BookRankHomeItemRespDto> out = new ArrayList<>();
        for (int i = 0; i < bookIdStrList.size(); i++) {
            long bookId = Long.parseLong(bookIdStrList.get(i));
            int rank = i + 1;
            @SuppressWarnings("unchecked")
            List<Object> vals = (List<Object>) pipelineResults.get(i);
            BookRankHomeItemRespDto row = parseHomeRowFromHmget(bookId, rank, vals, i == 0);
            if (row != null && row.getBookName() != null) {
                out.add(row);
            } else {
                BookInfo b = bookInfoMapper.selectById(bookId);
                if (b != null) {
                    out.add(singleDbBookToHomeItem(b, rank, i == 0));
                }
            }
        }
        return out;
    }

    private BookRankHomeItemRespDto singleDbBookToHomeItem(BookInfo b, int rank, boolean top) {
        BookRankHomeItemRespDto dto = new BookRankHomeItemRespDto();
        dto.setRank(rank);
        dto.setId(b.getId());
        dto.setBookName(b.getBookName());
        if (top) {
            dto.setPicUrl(b.getPicUrl());
            dto.setBookDesc(RankBookDescUtils.toRankPreview(b.getBookDesc()));
        }
        return dto;
    }

    private BookRankTableRespDto parseTableRowFromHmget(long bookId, int rank, List<Object> vals) {
        if (vals == null || vals.size() < 5) {
            return null;
        }
        BookRankTableRespDto dto = new BookRankTableRespDto();
        dto.setRank(rank);
        dto.setId(bookId);
        dto.setCategoryName(stringVal(vals.get(0)));
        dto.setBookName(stringVal(vals.get(1)));
        dto.setLastChapterName(stringVal(vals.get(2)));
        dto.setAuthorName(stringVal(vals.get(3)));
        dto.setWordCount(parseIntWordCount(vals.get(4)));
        return dto;
    }

    private BookRankHomeItemRespDto parseHomeRowFromHmget(long bookId, int rank, List<Object> vals, boolean top) {
        if (vals == null || vals.isEmpty()) {
            return null;
        }
        BookRankHomeItemRespDto dto = new BookRankHomeItemRespDto();
        dto.setRank(rank);
        dto.setId(bookId);
        dto.setBookName(stringVal(vals.get(0)));
        if (top && vals.size() >= 3) {
            dto.setPicUrl(stringVal(vals.get(1)));
            dto.setBookDesc(RankBookDescUtils.toRankPreview(stringVal(vals.get(2))));
        }
        return dto;
    }

    private static String stringVal(Object o) {
        return o == null ? null : Objects.toString(o, null);
    }

    private static Integer parseIntWordCount(Object o) {
        if (o == null) {
            return null;
        }
        try {
            return Integer.parseInt(o.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
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
        // 1. 尝试从 Redis 获取缓存
        String cacheKey = CacheConsts.BOOK_REC_CACHE_NAME + "::" + bookId;
        String cacheValue = stringRedisTemplate.opsForValue().get(cacheKey);

        if (cacheValue != null) {
            try {
                List<BookInfoRespDto> list = objectMapper.readValue(cacheValue, new TypeReference<List<BookInfoRespDto>>() {});
                log.info(">>> 相关推荐命中 Redis 缓存，bookId={}", bookId);
                return RestResp.ok(list);
            } catch (Exception e) {
                log.warn("解析相关推荐缓存失败，key={}", cacheKey, e);
            }
        }

        log.info(">>> 相关推荐未命中缓存，回源查询 DB，bookId={}", bookId);

        // 2. 查 DB
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
        
        // 3. 写入缓存 (24小时)
        try {
            if (!CollectionUtils.isEmpty(result)) {
                String json = objectMapper.writeValueAsString(result);
                stringRedisTemplate.opsForValue().set(cacheKey, json, 24, TimeUnit.HOURS);
                log.info(">>> 相关推荐已写入 Redis，bookId={}", bookId);
            }
        } catch (Exception e) {
            log.error("写入相关推荐缓存失败, bookId={}", bookId, e);
        }
        
        return RestResp.ok(result);
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

    /**
     * 构建书籍信息 Map (用于 Redis Hash)
     */
    private Map<String, String> buildBookInfoMap(BookInfo book, Integer firstChapterNum) {
        Map<String, String> map = new HashMap<>();
        map.put("id", String.valueOf(book.getId()));
        map.put("bookName", book.getBookName());
        map.put("authorId", String.valueOf(book.getAuthorId()));
        map.put("authorName", book.getAuthorName());
        map.put("picUrl", book.getPicUrl());
        map.put("bookDesc", book.getBookDesc());
        map.put("categoryName", book.getCategoryName());
        map.put("bookStatus", String.valueOf(book.getBookStatus()));
        map.put("visitCount", String.valueOf(book.getVisitCount()));
        map.put("lastChapterName", book.getLastChapterName());
        map.put("firstChapterNum", String.valueOf(firstChapterNum));

        if (book.getLastChapterNum() != null) map.put("lastChapterNum", String.valueOf(book.getLastChapterNum()));
        if (book.getWordCount() != null) map.put("wordCount", String.valueOf(book.getWordCount()));
        if (book.getCategoryId() != null) map.put("categoryId", String.valueOf(book.getCategoryId()));
        if (book.getScore() != null) map.put("score", String.valueOf(book.getScore()));
        if (book.getCommentCount() != null) map.put("commentCount", String.valueOf(book.getCommentCount()));
        if (book.getWorkDirection() != null) map.put("workDirection", String.valueOf(book.getWorkDirection()));

        if (book.getUpdateTime() != null) {
            map.put("updateTime", book.getUpdateTime().format(DATE_TIME_FORMATTER));
        }
        if (book.getLastChapterUpdateTime() != null) {
            map.put("lastChapterUpdateTime", book.getLastChapterUpdateTime().format(DATE_TIME_FORMATTER));
        }
        return map;
    }

}

