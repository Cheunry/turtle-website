package com.novel.book.job;

import com.novel.common.constant.CacheConsts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.Set;

/**
 * UV 去重集合监控任务：
 * 1. UV key 总量监控
 * 2. 热门书籍 UV 集合基数（SCARD）监控
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BookVisitUvMonitorJob {

    private static final String UV_KEY_COUNT_ALERT_PREFIX = "[MONITOR_ALERT][VISIT_UV_KEY_COUNT]";
    private static final String UV_HOT_SCARD_ALERT_PREFIX = "[MONITOR_ALERT][VISIT_UV_HOT_SCARD]";

    private final StringRedisTemplate stringRedisTemplate;

    @Scheduled(cron = "0 */5 * * * ?")
    public void monitorVisitUvMetrics() {
        monitorUvKeyCount();
        monitorUvHotScard();
    }

    private void monitorUvKeyCount() {
        Long uvKeyCount = stringRedisTemplate.execute((RedisCallback<Long>) connection -> scanKeyCount(connection,
                CacheConsts.BOOK_VISIT_UV_SET_PREFIX + "*"));
        if (uvKeyCount == null) {
            return;
        }
        if (uvKeyCount > CacheConsts.BOOK_VISIT_UV_KEY_COUNT_ALERT_THRESHOLD) {
            log.error("{} uvKeyCount={}, threshold={}",
                    UV_KEY_COUNT_ALERT_PREFIX,
                    uvKeyCount,
                    CacheConsts.BOOK_VISIT_UV_KEY_COUNT_ALERT_THRESHOLD);
        } else {
            log.info("[METRIC][VISIT_UV_KEY_COUNT] uvKeyCount={}, threshold={}",
                    uvKeyCount, CacheConsts.BOOK_VISIT_UV_KEY_COUNT_ALERT_THRESHOLD);
        }
    }

    private void monitorUvHotScard() {
        Set<String> topBookIds = stringRedisTemplate.opsForZSet().reverseRange(
                CacheConsts.BOOK_VISIT_RANK_ZSET, 0, CacheConsts.BOOK_VISIT_UV_MONITOR_SAMPLE_SIZE - 1);
        if (CollectionUtils.isEmpty(topBookIds)) {
            return;
        }
        long currentBucket = (System.currentTimeMillis() / 1000) / CacheConsts.BOOK_VISIT_UV_WINDOW_SECONDS;
        for (String bookId : topBookIds) {
            String uvKey = CacheConsts.BOOK_VISIT_UV_SET_PREFIX + bookId + ":" + currentBucket;
            Long scard = stringRedisTemplate.opsForSet().size(uvKey);
            if (scard == null) {
                continue;
            }
            if (scard > CacheConsts.BOOK_VISIT_UV_SCARD_HOT_ALERT_THRESHOLD) {
                log.error("{} bookId={}, uvKey={}, scard={}, threshold={}",
                        UV_HOT_SCARD_ALERT_PREFIX,
                        bookId,
                        uvKey,
                        scard,
                        CacheConsts.BOOK_VISIT_UV_SCARD_HOT_ALERT_THRESHOLD);
            }
        }
    }

    private long scanKeyCount(RedisConnection connection, String pattern) {
        ScanOptions options = ScanOptions.scanOptions()
                .match(pattern)
                .count(1000)
                .build();
        long count = 0L;
        try (Cursor<byte[]> cursor = connection.scan(options)) {
            while (cursor.hasNext()) {
                cursor.next();
                count++;
            }
        } catch (Exception e) {
            log.warn("扫描UV key数量失败，pattern={}", pattern, e);
        }
        return count;
    }
}
