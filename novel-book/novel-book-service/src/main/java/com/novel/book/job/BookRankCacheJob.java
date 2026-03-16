package com.novel.book.job;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.novel.book.dao.entity.BookInfo;
import com.novel.book.dao.mapper.BookInfoMapper;
import com.novel.book.dao.mapper.BookChapterMapper;
import com.novel.common.constant.CacheConsts;
import com.novel.common.constant.DatabaseConsts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookRankCacheJob {

    private final BookInfoMapper bookInfoMapper;
    private final BookChapterMapper bookChapterMapper;
    private final StringRedisTemplate stringRedisTemplate;

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 应用启动完成后初始化缓存
     * 说明：在应用完全启动后（所有Bean都初始化完成），初始化所有榜单缓存
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initCacheOnStartup() {
        log.info("应用启动完成，开始初始化榜单缓存...");
        try {
            // 初始化点击榜缓存
            refreshVisitRankCache();
            // 初始化新书榜缓存
            refreshNewestRankCache();
            // 初始化更新榜缓存
            refreshUpdateRankCache();
            log.info("榜单缓存初始化完成");
        } catch (Exception e) {
            log.error("启动时初始化榜单缓存失败", e);
        }
    }

    /**
     * 刷新小说点击榜缓存（兜底策略）
     * 说明：
     * 1. ZSet 排行榜：用户访问时已通过 Lua 脚本实时更新，这里只做兜底（如果 ZSet 不存在或数据异常才重建）
     * 2. 详情 Hash：更新时保留 visitCount 字段，不覆盖实时更新的访问量
     * 3. 频率：改为每30分钟执行一次（因为主要是兜底，不需要太频繁）
     */
    @Scheduled(cron = "0 0/30 * * * ?")
    public void refreshVisitRankCache() {
        log.info("开始刷新小说点击榜缓存（兜底策略）...");

        // 1. 检查 ZSet 是否存在，如果存在且数据正常，则跳过 ZSet 重建
        // 修复：不仅检查是否为空，还要检查数据量是否足够（少于100本认为数据不完整，需要重建）
        Long zsetSize = stringRedisTemplate.opsForZSet().size(CacheConsts.BOOK_VISIT_RANK_ZSET);
        boolean needRebuildZSet = (zsetSize == null || zsetSize == 0 || zsetSize < 100);
        
        if (needRebuildZSet) {
            if (zsetSize == null || zsetSize == 0) {
                log.warn("ZSet 排行榜不存在或为空，从数据库重建...");
            } else {
                log.warn("ZSet 排行榜数据不完整（当前大小：{}，期望至少100本），从数据库重建...", zsetSize);
            }
        } else {
            log.info("ZSet 排行榜存在且正常（大小：{}），跳过重建，保留实时更新的数据", zsetSize);
        }

        // 2. 查询数据库 Top 100 书籍（用于更新详情 Hash 缓存）
        // 注意：只查询审核通过的书籍，且字数大于0
        List<BookInfo> bookInfos = bookInfoMapper.selectList(new QueryWrapper<BookInfo>()
                .eq("audit_status", 1)
                .gt(DatabaseConsts.BookTable.COLUMN_WORD_COUNT, 0)
                .orderByDesc(DatabaseConsts.BookTable.COLUMN_VISIT_COUNT)
                .last("limit 100"));
        if (bookInfos.isEmpty()) {
            log.warn("数据库中没有书籍数据，跳过缓存刷新");
            return;
        }
        log.info("查询到 {} 本书籍，准备写入详情Hash缓存", bookInfos.size());

        // 3. 批量查询首章
        List<Long> bookIds = bookInfos.stream().map(BookInfo::getId).collect(Collectors.toList());
        Map<Long, Integer> firstChapterMap = bookChapterMapper.selectFirstChapterNums(bookIds)
                .stream().collect(Collectors.toMap(
                        m -> Long.valueOf(m.get("bookId").toString()),
                        m -> Integer.valueOf(m.get("firstChapterNum").toString()),
                        (v1, v2) -> v1));

        // 4. 准备详情 Hash 数据（不包含 visitCount，避免覆盖实时更新的值）
        Map<String, Map<String, String>> allBookHashes = new HashMap<>();
        for (BookInfo book : bookInfos) {
            String hashKey = CacheConsts.BOOK_INFO_HASH_PREFIX + book.getId();
            allBookHashes.put(hashKey, buildBookInfoMap(book, firstChapterMap.getOrDefault(book.getId(), 1)));
        }

        // 5. 如果需要重建 ZSet，按优先级读取实时访问量
        // 优先级：ZSet本身（如果部分存在） > 数据库值 + 访问量缓冲Hash增量（最准确） > 详情Hash > 数据库
        if (needRebuildZSet) {
            Set<ZSetOperations.TypedTuple<String>> tuples = new HashSet<>();
            // 批量读取访问量缓冲Hash（存储的是增量，需要与数据库值相加）
            Map<Object, Object> visitCountHash = stringRedisTemplate.opsForHash().entries(CacheConsts.BOOK_VISIT_COUNT_HASH);
            
            for (BookInfo book : bookInfos) {
                String bookIdStr = String.valueOf(book.getId());
                Double score = null;
                
                // 优先级1：从ZSet本身读取（如果ZSet部分存在，说明是最实时的）
                Double zsetScore = stringRedisTemplate.opsForZSet().score(CacheConsts.BOOK_VISIT_RANK_ZSET, bookIdStr);
                if (zsetScore != null) {
                    score = zsetScore;
                    log.debug("从ZSet读取访问量，bookId={}, visitCount={}", bookIdStr, score);
                } else {
                    // 优先级2：计算总访问量 = 数据库值 + 访问量缓冲Hash中的增量（最准确）
                    long dbVisitCount = book.getVisitCount();
                    long bufferIncrement = 0;
                    if (visitCountHash != null && visitCountHash.containsKey(bookIdStr)) {
                        try {
                            bufferIncrement = Long.parseLong(visitCountHash.get(bookIdStr).toString());
                        } catch (NumberFormatException e) {
                            log.warn("访问量缓冲Hash格式错误，bookId={}, value={}", bookIdStr, visitCountHash.get(bookIdStr));
                        }
                    }
                    long totalVisitCount = dbVisitCount + bufferIncrement;
                    score = (double) totalVisitCount;
                    log.debug("计算总访问量，bookId={}, dbVisitCount={}, bufferIncrement={}, total={}", 
                            bookIdStr, dbVisitCount, bufferIncrement, totalVisitCount);
                }
                
                if (score != null && score > 0) {
                    tuples.add(new DefaultTypedTuple<>(bookIdStr, score));
                } else {
                    log.warn("访问量为0或无效，跳过书籍 bookId={}, score={}", bookIdStr, score);
                }
            }
            
            if (!tuples.isEmpty()) {
                // 删除旧的 ZSet 并重建（只在兜底时执行）
                stringRedisTemplate.delete(CacheConsts.BOOK_VISIT_RANK_ZSET);
                stringRedisTemplate.opsForZSet().add(CacheConsts.BOOK_VISIT_RANK_ZSET, tuples);
                log.info("ZSet 排行榜重建完成，共 {} 本书", tuples.size());
            }
        }

        // 6. Pipeline 批量更新详情 Hash（保留 visitCount 字段，不覆盖）
        // 注意：无论ZSet是否需要重建，都要更新详情Hash，确保所有榜单书籍的详情都被缓存
        log.info("开始批量写入 {} 本书籍的详情Hash缓存", allBookHashes.size());
        try {
            // 先批量检查哪些Hash已存在（在Pipeline外检查，避免Pipeline内检查不准确）
            Set<String> existingHashes = new HashSet<>();
            for (String hashKey : allBookHashes.keySet()) {
                if (stringRedisTemplate.hasKey(hashKey)) {
                    existingHashes.add(hashKey);
                }
            }
            log.debug("检查到 {} 个Hash已存在，{} 个Hash需要新建", existingHashes.size(), allBookHashes.size() - existingHashes.size());
            
            stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                var serializer = stringRedisTemplate.getStringSerializer();
                allBookHashes.forEach((hashKey, mapData) -> {
                    byte[] hashKeyBytes = serializer.serialize(hashKey);
                    boolean hashExists = existingHashes.contains(hashKey);
                    Map<byte[], byte[]> byteMap = new HashMap<>();
                    
                    if (hashExists) {
                        // Hash 已存在，排除 visitCount 字段，避免覆盖实时更新的值
                        mapData.forEach((k, v) -> {
                            if (!"visitCount".equals(k)) {
                                byteMap.put(serializer.serialize(k), serializer.serialize(v));
                            }
                        });
                    } else {
                        // Hash 不存在，包含所有字段（包括 visitCount）
                        mapData.forEach((k, v) -> 
                            byteMap.put(serializer.serialize(k), serializer.serialize(v))
                        );
                    }
                    
                    if (!byteMap.isEmpty()) {
                        connection.hashCommands().hMSet(hashKeyBytes, byteMap);
                    }
                });
                return null;
            });
            log.info("详情Hash缓存写入完成，共写入 {} 本书籍的详情（已存在：{}，新建：{}）", 
                    allBookHashes.size(), existingHashes.size(), allBookHashes.size() - existingHashes.size());
        } catch (Exception e) {
            log.error("批量写入详情Hash缓存失败", e);
        }
        
        log.info("小说点击榜缓存刷新完成（ZSet重建：{}，详情Hash已更新：{}本）", needRebuildZSet ? "是" : "否", allBookHashes.size());
    }

    /**
     * 刷新小说新书榜缓存并写入书籍详情
     * 说明：
     * 1. 查询新书榜Top 30书籍
     * 2. 将榜单中的书籍详情写入Redis Hash缓存
     * 3. 频率：每30分钟执行一次
     */
    @Scheduled(cron = "0 5/30 * * * ?")
    public void refreshNewestRankCache() {
        log.info("开始刷新小说新书榜缓存并写入书籍详情...");
        refreshRankBooksDetail(
                new QueryWrapper<BookInfo>()
                        .gt(DatabaseConsts.BookTable.COLUMN_WORD_COUNT, 0)
                        .eq("audit_status", 1)
                        .orderByDesc(DatabaseConsts.CommonColumnEnum.CREATE_TIME.getName())
                        .last("limit 30"),
                "新书榜"
        );
    }

    /**
     * 刷新小说更新榜缓存并写入书籍详情
     * 说明：
     * 1. 查询更新榜Top 30书籍
     * 2. 将榜单中的书籍详情写入Redis Hash缓存
     * 3. 频率：每30分钟执行一次
     */
    @Scheduled(cron = "0 10/30 * * * ?")
    public void refreshUpdateRankCache() {
        log.info("开始刷新小说更新榜缓存并写入书籍详情...");
        refreshRankBooksDetail(
                new QueryWrapper<BookInfo>()
                        .gt(DatabaseConsts.BookTable.COLUMN_WORD_COUNT, 0)
                        .eq("audit_status", 1)
                        .orderByDesc(DatabaseConsts.CommonColumnEnum.UPDATE_TIME.getName())
                        .last("limit 30"),
                "更新榜"
        );
    }

    /**
     * 通用的榜单书籍详情缓存刷新方法
     * @param queryWrapper 查询条件
     * @param rankName 榜单名称（用于日志）
     */
    private void refreshRankBooksDetail(QueryWrapper<BookInfo> queryWrapper, String rankName) {
        try {
            // 1. 查询榜单书籍列表
            List<BookInfo> bookInfos = bookInfoMapper.selectList(queryWrapper);
            if (bookInfos.isEmpty()) {
                log.warn("{}中没有书籍数据，跳过缓存刷新", rankName);
                return;
            }

            // 2. 批量查询首章
            List<Long> bookIds = bookInfos.stream().map(BookInfo::getId).collect(Collectors.toList());
            Map<Long, Integer> firstChapterMap = bookChapterMapper.selectFirstChapterNums(bookIds)
                    .stream().collect(Collectors.toMap(
                            m -> Long.valueOf(m.get("bookId").toString()),
                            m -> Integer.valueOf(m.get("firstChapterNum").toString()),
                            (v1, v2) -> v1));

            // 3. 准备详情 Hash 数据
            Map<String, Map<String, String>> allBookHashes = new HashMap<>();
            for (BookInfo book : bookInfos) {
                String hashKey = CacheConsts.BOOK_INFO_HASH_PREFIX + book.getId();
                allBookHashes.put(hashKey, buildBookInfoMap(book, firstChapterMap.getOrDefault(book.getId(), 1)));
            }

            // 4. Pipeline 批量更新详情 Hash（保留 visitCount 字段，不覆盖实时更新的值）
            log.info("开始批量写入 {} 本书籍的详情Hash缓存", allBookHashes.size());
            try {
                // 先批量检查哪些Hash已存在（在Pipeline外检查，避免Pipeline内检查不准确）
                Set<String> existingHashes = new HashSet<>();
                for (String hashKey : allBookHashes.keySet()) {
                    if (stringRedisTemplate.hasKey(hashKey)) {
                        existingHashes.add(hashKey);
                    }
                }
                log.debug("检查到 {} 个Hash已存在，{} 个Hash需要新建", existingHashes.size(), allBookHashes.size() - existingHashes.size());
                
                stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                    var serializer = stringRedisTemplate.getStringSerializer();
                    allBookHashes.forEach((hashKey, mapData) -> {
                        byte[] hashKeyBytes = serializer.serialize(hashKey);
                        boolean hashExists = existingHashes.contains(hashKey);
                        Map<byte[], byte[]> byteMap = new HashMap<>();
                        
                        if (hashExists) {
                            // Hash 已存在，排除 visitCount 字段，避免覆盖实时更新的值
                            mapData.forEach((k, v) -> {
                                if (!"visitCount".equals(k)) {
                                    byteMap.put(serializer.serialize(k), serializer.serialize(v));
                                }
                            });
                        } else {
                            // Hash 不存在，包含所有字段（包括 visitCount）
                            mapData.forEach((k, v) -> 
                                byteMap.put(serializer.serialize(k), serializer.serialize(v))
                            );
                        }
                        
                        if (!byteMap.isEmpty()) {
                            connection.hashCommands().hMSet(hashKeyBytes, byteMap);
                        }
                    });
                    return null;
                });
                log.info("{}缓存刷新完成，共 {} 本书籍详情已写入Redis（已存在：{}，新建：{}）", 
                        rankName, allBookHashes.size(), existingHashes.size(), allBookHashes.size() - existingHashes.size());
            } catch (Exception e) {
                log.error("批量写入{}详情Hash缓存失败", rankName, e);
            }
        } catch (Exception e) {
            log.error("刷新{}缓存失败", rankName, e);
        }
    }

    /**
     * 将 BookInfo 对象转换为符合缓存要求的 Map
     * 注意：visitCount 字段会在更新时根据 Hash 是否存在来决定是否包含
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
        // visitCount 字段：如果 Hash 已存在则不会包含（避免覆盖实时更新的值），如果不存在则包含（初始化）
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