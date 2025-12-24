package com.novel.book.job;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.novel.book.dao.entity.BookInfo;
import com.novel.book.dao.mapper.BookInfoMapper;
import com.novel.common.constant.CacheConsts;
import com.novel.common.constant.DatabaseConsts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 小说排行榜缓存预热任务
 */
import com.novel.book.dao.entity.BookChapter;
import com.novel.book.dao.mapper.BookChapterMapper;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookRankCacheJob {

    private final BookInfoMapper bookInfoMapper;
    private final BookChapterMapper bookChapterMapper;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 每 10 分钟刷新一次点击榜缓存
     */
    @Scheduled(cron = "0 0/10 * * * ?")
    public void refreshVisitRankCache() {
        log.info("开始刷新小说点击榜缓存...");

        // 1. 查询点击量最高的 Top 100 书籍 (用于 ZSet)
        QueryWrapper<BookInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.orderByDesc(DatabaseConsts.BookTable.COLUMN_VISIT_COUNT);
        queryWrapper.last("limit 100");
        List<BookInfo> bookInfos = bookInfoMapper.selectList(queryWrapper);

        if (bookInfos.isEmpty()) {
            return;
        }

        // 2. 更新 Redis ZSet
        // 删除旧的 ZSet，防止无限膨胀
        stringRedisTemplate.delete(CacheConsts.BOOK_VISIT_RANK_ZSET);
        for (BookInfo book : bookInfos) {
            stringRedisTemplate.opsForZSet().add(CacheConsts.BOOK_VISIT_RANK_ZSET, String.valueOf(book.getId()), book.getVisitCount());
        }

        // 3. 预热 Hash (Top 100) - 包含详情页所需字段
        int preheatCount = 100;
        for (int i = 0; i < bookInfos.size() && i < preheatCount; i++) {
            BookInfo book = bookInfos.get(i);
            String key = CacheConsts.BOOK_INFO_HASH_PREFIX + book.getId();
            Map<String, String> map = new HashMap<>();
            
            // 基础信息
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
            if (book.getLastChapterNum() != null) map.put("lastChapterNum", String.valueOf(book.getLastChapterNum()));
            if (book.getWordCount() != null) map.put("wordCount", String.valueOf(book.getWordCount()));
            if (book.getCategoryId() != null) map.put("categoryId", String.valueOf(book.getCategoryId()));
            if (book.getScore() != null) map.put("score", String.valueOf(book.getScore()));
            if (book.getCommentCount() != null) map.put("commentCount", String.valueOf(book.getCommentCount()));
            if (book.getWorkDirection() != null) map.put("workDirection", String.valueOf(book.getWorkDirection()));
            if (book.getUpdateTime() != null) {
                 map.put("updateTime", book.getUpdateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            }
            if (book.getLastChapterUpdateTime() != null) {
                map.put("lastChapterUpdateTime", book.getLastChapterUpdateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            }

            // 查询首章 ID (详情页开始阅读需要)
            QueryWrapper<BookChapter> chapterQueryWrapper = new QueryWrapper<>();
            chapterQueryWrapper.eq(DatabaseConsts.BookChapterTable.COLUMN_BOOK_ID, book.getId())
                    .eq("audit_status", 1)
                    .orderByAsc(DatabaseConsts.BookChapterTable.COLUMN_CHAPTER_NUM)
                    .last(DatabaseConsts.SqlEnum.LIMIT_1.getSql());
            BookChapter firstChapter = bookChapterMapper.selectOne(chapterQueryWrapper);
            if (firstChapter != null) {
                map.put("firstChapterNum", String.valueOf(firstChapter.getChapterNum()));
            } else {
                map.put("firstChapterNum", "1"); // 默认值
            }
            
            stringRedisTemplate.opsForHash().putAll(key, map);
        }
        
        log.info("小说点击榜缓存刷新完成，共处理 {} 本书籍", bookInfos.size());
    }
}

