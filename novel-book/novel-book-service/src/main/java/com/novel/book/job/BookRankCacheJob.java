package com.novel.book.job;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.novel.book.dao.entity.BookInfo;
import com.novel.book.dao.mapper.BookInfoMapper;
import com.novel.book.dao.mapper.BookChapterMapper;
import com.novel.common.constant.CacheConsts;
import com.novel.common.constant.DatabaseConsts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    @Scheduled(cron = "0 0/2 * * * ?")
    public void refreshVisitRankCache() {
        log.info("开始刷新小说点击榜缓存...");

        // 1. 查询数据（保持不变）
        List<BookInfo> bookInfos = bookInfoMapper.selectList(new QueryWrapper<BookInfo>()
                .orderByDesc(DatabaseConsts.BookTable.COLUMN_VISIT_COUNT)
                .last("limit 100"));
        if (bookInfos.isEmpty()) return;

        // 2. 批量查询首章（保持不变）
        List<Long> bookIds = bookInfos.stream().map(BookInfo::getId).collect(Collectors.toList());
        Map<Long, Integer> firstChapterMap = bookChapterMapper.selectFirstChapterNums(bookIds)
                .stream().collect(Collectors.toMap(
                        m -> Long.valueOf(m.get("bookId").toString()),
                        m -> Integer.valueOf(m.get("firstChapterNum").toString()),
                        (v1, v2) -> v1));

        // --- 优化点 A: 提前准备好要写入 Redis 的数据 ---
        // 这样做可以缩短 Redis Connection 的占用时间
        Map<String, Map<String, String>> allBookHashes = new HashMap<>();
        for (BookInfo book : bookInfos) {
            String hashKey = CacheConsts.BOOK_INFO_HASH_PREFIX + book.getId();
            allBookHashes.put(hashKey, buildBookInfoMap(book, firstChapterMap.getOrDefault(book.getId(), 1)));
        }

        // --- 优化点 B: 极致的 Redis 操作 ---
        
        // 1. 准备 ZSet 数据
        Set<ZSetOperations.TypedTuple<String>> tuples = new HashSet<>();
        allBookHashes.forEach((hashKey, mapData) -> {
            String bookIdStr = hashKey.substring(CacheConsts.BOOK_INFO_HASH_PREFIX.length());
            Double score = Double.valueOf(mapData.get("visitCount"));
            tuples.add(new DefaultTypedTuple<>(bookIdStr, score));
        });

        // 2. 更新 ZSet (删除旧的，批量写入新的)
        // 使用 opsForZSet().add 批量添加，底层对应 ZADD key score member [score member ...]
        stringRedisTemplate.delete(CacheConsts.BOOK_VISIT_RANK_ZSET);
        if (!tuples.isEmpty()) {
            stringRedisTemplate.opsForZSet().add(CacheConsts.BOOK_VISIT_RANK_ZSET, tuples);
        }

        // 3. Pipeline 批量写入 Hash (减少网络 RTT)
        stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            var serializer = stringRedisTemplate.getStringSerializer();
            allBookHashes.forEach((hashKey, mapData) -> {
                byte[] hashKeyBytes = serializer.serialize(hashKey);
                Map<byte[], byte[]> byteMap = new HashMap<>();
                mapData.forEach((k, v) -> byteMap.put(serializer.serialize(k), serializer.serialize(v)));
                connection.hMSet(hashKeyBytes, byteMap);
            });
            return null;
        });

        log.info("小说点击榜缓存刷新完成");
    }


    /**
     * 将 BookInfo 对象转换为符合缓存要求的 Map
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

//package com.novel.book.job;
//
//import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
//import com.novel.book.dao.entity.BookInfo;
//import com.novel.book.dao.mapper.BookInfoMapper;
//import com.novel.common.constant.CacheConsts;
//import com.novel.common.constant.DatabaseConsts;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.data.redis.core.StringRedisTemplate;
//import org.springframework.scheduling.annotation.Scheduled;
//import org.springframework.stereotype.Component;
//
//import java.time.format.DateTimeFormatter;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//
///**
// * 小说排行榜缓存预热任务
// */
//import com.novel.book.dao.entity.BookChapter;
//import com.novel.book.dao.mapper.BookChapterMapper;
//
//@Component
//@RequiredArgsConstructor
//@Slf4j
//public class BookRankCacheJob {
//
//    private final BookInfoMapper bookInfoMapper;
//    private final BookChapterMapper bookChapterMapper;
//    private final StringRedisTemplate stringRedisTemplate;
//
//    /**
//     * 每 10 分钟刷新一次点击榜缓存
//     */
//    @Scheduled(cron = "0 0/10 * * * ?")
//    public void refreshVisitRankCache() {
//        log.info("开始刷新小说点击榜缓存...");
//
//        // 1. 查询点击量最高的 Top 100 书籍 (用于 ZSet)
//        QueryWrapper<BookInfo> queryWrapper = new QueryWrapper<>();
//        queryWrapper.orderByDesc(DatabaseConsts.BookTable.COLUMN_VISIT_COUNT);
//        queryWrapper.last("limit 100");
//        List<BookInfo> bookInfos = bookInfoMapper.selectList(queryWrapper);
//
//        if (bookInfos.isEmpty()) {
//            return;
//        }
//
//        // 2. 更新 Redis ZSet
//        // 删除旧的 ZSet，防止无限膨胀
//        stringRedisTemplate.delete(CacheConsts.BOOK_VISIT_RANK_ZSET);
//        for (BookInfo book : bookInfos) {
//            stringRedisTemplate.opsForZSet().add(CacheConsts.BOOK_VISIT_RANK_ZSET, String.valueOf(book.getId()), book.getVisitCount());
//        }
//
//        // 3. 预热 Hash (Top 100) - 包含详情页所需字段
//        int preheatCount = 100;
//        for (int i = 0; i < bookInfos.size() && i < preheatCount; i++) {
//            BookInfo book = bookInfos.get(i);
//            String key = CacheConsts.BOOK_INFO_HASH_PREFIX + book.getId();
//            Map<String, String> map = new HashMap<>();
//
//            // 基础信息
//            map.put("id", String.valueOf(book.getId()));
//            map.put("bookName", book.getBookName());
//            map.put("authorId", String.valueOf(book.getAuthorId()));
//            map.put("authorName", book.getAuthorName());
//            map.put("picUrl", book.getPicUrl());
//            map.put("bookDesc", book.getBookDesc());
//            map.put("categoryName", book.getCategoryName());
//            map.put("bookStatus", String.valueOf(book.getBookStatus()));
//            map.put("visitCount", String.valueOf(book.getVisitCount()));
//            map.put("lastChapterName", book.getLastChapterName());
//            if (book.getLastChapterNum() != null) map.put("lastChapterNum", String.valueOf(book.getLastChapterNum()));
//            if (book.getWordCount() != null) map.put("wordCount", String.valueOf(book.getWordCount()));
//            if (book.getCategoryId() != null) map.put("categoryId", String.valueOf(book.getCategoryId()));
//            if (book.getScore() != null) map.put("score", String.valueOf(book.getScore()));
//            if (book.getCommentCount() != null) map.put("commentCount", String.valueOf(book.getCommentCount()));
//            if (book.getWorkDirection() != null) map.put("workDirection", String.valueOf(book.getWorkDirection()));
//            if (book.getUpdateTime() != null) {
//                 map.put("updateTime", book.getUpdateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
//            }
//            if (book.getLastChapterUpdateTime() != null) {
//                map.put("lastChapterUpdateTime", book.getLastChapterUpdateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
//            }
//
//            // 查询首章 ID (详情页开始阅读需要)
//            QueryWrapper<BookChapter> chapterQueryWrapper = new QueryWrapper<>();
//            chapterQueryWrapper.eq(DatabaseConsts.BookChapterTable.COLUMN_BOOK_ID, book.getId())
//                    .eq("audit_status", 1)
//                    .orderByAsc(DatabaseConsts.BookChapterTable.COLUMN_CHAPTER_NUM)
//                    .last(DatabaseConsts.SqlEnum.LIMIT_1.getSql());
//            BookChapter firstChapter = bookChapterMapper.selectOne(chapterQueryWrapper);
//            if (firstChapter != null) {
//                map.put("firstChapterNum", String.valueOf(firstChapter.getChapterNum()));
//            } else {
//                map.put("firstChapterNum", "1"); // 默认值
//            }
//
//            stringRedisTemplate.opsForHash().putAll(key, map);
//        }
//
//        log.info("小说点击榜缓存刷新完成，共处理 {} 本书籍", bookInfos.size());
//    }
//}
//

//    @Scheduled(cron = "0 0/10 * * * ?")
//    public void refreshVisitRankCache() {
//        log.info("开始刷新小说点击榜缓存...");
//
//        // 1. 批量查询点击量最高的 Top 100 书籍 (1次 SQL)
//        List<BookInfo> bookInfos = bookInfoMapper.selectList(new QueryWrapper<BookInfo>()
//                .orderByDesc(DatabaseConsts.BookTable.COLUMN_VISIT_COUNT)
//                .last("limit 100"));
//
//        if (bookInfos.isEmpty()) {
//            return;
//        }
//
//        // 2. 核心优化：批量查询首章编号 (1次 SQL，消除循环查库)
//        List<Long> bookIds = bookInfos.stream().map(BookInfo::getId).collect(Collectors.toList());
//        List<Map<String, Object>> chapterList = bookChapterMapper.selectFirstChapterNums(bookIds);
//        Map<Long, Integer> firstChapterMap = chapterList.stream()
//                .collect(Collectors.toMap(
//                        m -> Long.valueOf(m.get("bookId").toString()),
//                        m -> Integer.valueOf(m.get("firstChapterNum").toString()),
//                        (v1, v2) -> v1 // 防止重复键
//                ));
//
//        // 3. 核心优化：使用 Pipeline 批量写入 Redis (1次网络往返，消除数百次 RTT)
//        stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
//            // A. 清理旧的 ZSet
//            byte[] zsetKey = stringRedisTemplate.getStringSerializer().serialize(CacheConsts.BOOK_VISIT_RANK_ZSET);
//            connection.del(zsetKey);
//
//            for (BookInfo book : bookInfos) {
//                // B. 写入 ZSet 排名
//                connection.zAdd(zsetKey,
//                        book.getVisitCount(),
//                        stringRedisTemplate.getStringSerializer().serialize(String.valueOf(book.getId())));
//
//                // C. 构建并写入 Hash 详情
//                String hashKey = CacheConsts.BOOK_INFO_HASH_PREFIX + book.getId();
//                Map<String, String> map = buildBookInfoMap(book, firstChapterMap.getOrDefault(book.getId(), 1));
//
//                byte[] hashKeyBytes = stringRedisTemplate.getStringSerializer().serialize(hashKey);
//                Map<byte[], byte[]> hashFieldValues = new HashMap<>();
//                map.forEach((k, v) -> hashFieldValues.put(
//                        stringRedisTemplate.getStringSerializer().serialize(k),
//                        stringRedisTemplate.getStringSerializer().serialize(v)
//                ));
//                connection.hMSet(hashKeyBytes, hashFieldValues);
//            }
//            return null;
//        });
//
//        log.info("小说点击榜缓存刷新完成，共处理 {} 本书籍", bookInfos.size());
//    }