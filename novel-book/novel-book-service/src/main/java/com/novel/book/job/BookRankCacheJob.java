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
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.util.CollectionUtils;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookRankCacheJob {
    private static final String MONITOR_ALERT_PREFIX = "[MONITOR_ALERT][VISIT_RANK_SCAN_ABORT]";

    private final BookInfoMapper bookInfoMapper;
    private final BookChapterMapper bookChapterMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ScheduledExecutorService lockWatchdogExecutor = Executors.newSingleThreadScheduledExecutor(
            r -> new Thread(r, "visit-rank-lock-watchdog"));

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = buildReleaseLockScript();
    private static final DefaultRedisScript<Long> RENEW_LOCK_SCRIPT = buildRenewLockScript();

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
     * 点击榜全量校准任务：
     * 1. 按主键游标分批扫描 MySQL（避免 visit_count 排序压力）
     * 2. 在 Redis 构建候选 TopK（默认200）
     * 3. 扫描完成后合并到实时榜单并裁剪
     */
    @Scheduled(cron = "0 */15 * * * ?")
    public void refreshVisitRankCache() {
        String lockToken = UUID.randomUUID().toString();
        Boolean lockSuccess = stringRedisTemplate.opsForValue().setIfAbsent(
                CacheConsts.BOOK_VISIT_RANK_SCAN_LOCK_KEY,
                lockToken,
                CacheConsts.BOOK_VISIT_RANK_SCAN_LOCK_TTL_SECONDS,
                TimeUnit.SECONDS);
        if (Boolean.FALSE.equals(lockSuccess)) {
            log.info("点击榜校准任务正在执行中，跳过本轮");
            return;
        }
        AtomicBoolean lockLost = new AtomicBoolean(false);
        AtomicInteger renewFailCount = new AtomicInteger(0);
        ScheduledFuture<?> watchdogFuture = startLockWatchdog(lockToken, lockLost, renewFailCount);
        String roundId = String.valueOf(System.currentTimeMillis());
        String buildKey = CacheConsts.BOOK_VISIT_RANK_BUILD_ZSET_PREFIX + roundId;
        try {
            log.info("开始点击榜全量校准，roundId={}", roundId);
            stringRedisTemplate.opsForValue().set(CacheConsts.BOOK_VISIT_RANK_SCAN_ROUND_ID_KEY, roundId);
            stringRedisTemplate.opsForValue().set(CacheConsts.BOOK_VISIT_RANK_SCAN_LAST_ID_KEY, "0");
            stringRedisTemplate.delete(buildKey);

            long lastId = 0L;
            int scanned = 0;
            while (true) {
                if (lockLost.get()) {
                    log.error("点击榜校准任务中止：看门狗连续续约失败，roundId={}, scanned={}", roundId, scanned);
                    emitScanAbortAlert(roundId, scanned, renewFailCount.get(), lockToken);
                    return;
                }
                List<BookInfo> batch = loadVisitScanBatch(lastId, CacheConsts.BOOK_VISIT_RANK_SCAN_BATCH_SIZE);
                if (CollectionUtils.isEmpty(batch)) {
                    break;
                }
                scanned += batch.size();
                appendBatchToBuildRank(buildKey, batch);
                trimRankZSet(buildKey, CacheConsts.BOOK_VISIT_RANK_CANDIDATE_SIZE);
                lastId = batch.get(batch.size() - 1).getId();
                stringRedisTemplate.opsForValue().set(CacheConsts.BOOK_VISIT_RANK_SCAN_LAST_ID_KEY, String.valueOf(lastId));

                if (batch.size() < CacheConsts.BOOK_VISIT_RANK_SCAN_BATCH_SIZE) {
                    break;
                }
            }

            mergeBuildRankToLive(buildKey);
            trimRankZSet(CacheConsts.BOOK_VISIT_RANK_ZSET, CacheConsts.BOOK_VISIT_RANK_CANDIDATE_SIZE);
            refreshVisitRankBookDetailCache();
            log.info("点击榜全量校准完成，roundId={}, 扫描书籍数={}", roundId, scanned);
        } catch (Exception e) {
            log.error("点击榜全量校准失败，roundId={}", roundId, e);
        } finally {
            if (watchdogFuture != null) {
                watchdogFuture.cancel(true);
            }
            stringRedisTemplate.delete(buildKey);
            releaseLock(lockToken);
            stringRedisTemplate.delete(CacheConsts.BOOK_VISIT_RANK_SCAN_LAST_ID_KEY);
            stringRedisTemplate.delete(CacheConsts.BOOK_VISIT_RANK_SCAN_ROUND_ID_KEY);
        }
    }

    private ScheduledFuture<?> startLockWatchdog(String lockToken, AtomicBoolean lockLost, AtomicInteger renewFailCount) {
        long interval = CacheConsts.BOOK_VISIT_RANK_SCAN_LOCK_WATCHDOG_INTERVAL_SECONDS;
        return lockWatchdogExecutor.scheduleAtFixedRate(
                () -> renewLock(lockToken, lockLost, renewFailCount), interval, interval, TimeUnit.SECONDS);
    }

    private void renewLock(String lockToken, AtomicBoolean lockLost, AtomicInteger renewFailCount) {
        try {
            Long result = stringRedisTemplate.execute(
                    RENEW_LOCK_SCRIPT,
                    List.of(CacheConsts.BOOK_VISIT_RANK_SCAN_LOCK_KEY),
                    lockToken,
                    String.valueOf(CacheConsts.BOOK_VISIT_RANK_SCAN_LOCK_TTL_SECONDS)
            );
            if (result == null || result == 0L) {
                int failCount = renewFailCount.incrementAndGet();
                log.warn("点击榜扫描锁续约失败，token={}, failCount={}", lockToken, failCount);
                if (failCount >= CacheConsts.BOOK_VISIT_RANK_SCAN_LOCK_WATCHDOG_MAX_FAIL_COUNT) {
                    lockLost.set(true);
                }
                return;
            }
            renewFailCount.set(0);
        } catch (Exception e) {
            int failCount = renewFailCount.incrementAndGet();
            log.warn("点击榜扫描锁续约异常，token={}, failCount={}", lockToken, failCount, e);
            if (failCount >= CacheConsts.BOOK_VISIT_RANK_SCAN_LOCK_WATCHDOG_MAX_FAIL_COUNT) {
                lockLost.set(true);
            }
        }
    }

    private void releaseLock(String lockToken) {
        try {
            stringRedisTemplate.execute(
                    RELEASE_LOCK_SCRIPT,
                    List.of(CacheConsts.BOOK_VISIT_RANK_SCAN_LOCK_KEY),
                    lockToken
            );
        } catch (Exception e) {
            log.warn("释放点击榜扫描锁失败，token={}", lockToken, e);
        }
    }

    private void emitScanAbortAlert(String roundId, int scanned, int renewFailCount, String lockToken) {
        // 结构化告警日志，方便日志平台按固定前缀和字段建立告警规则
        log.error("{} roundId={}, scanned={}, renewFailCount={}, lockKey={}, lockToken={}",
                MONITOR_ALERT_PREFIX,
                roundId,
                scanned,
                renewFailCount,
                CacheConsts.BOOK_VISIT_RANK_SCAN_LOCK_KEY,
                lockToken);
    }

    private static DefaultRedisScript<Long> buildReleaseLockScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setResultType(Long.class);
        script.setScriptText(
                "if redis.call('GET', KEYS[1]) == ARGV[1] then " +
                        "return redis.call('DEL', KEYS[1]) " +
                        "else return 0 end");
        return script;
    }

    private static DefaultRedisScript<Long> buildRenewLockScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setResultType(Long.class);
        script.setScriptText(
                "if redis.call('GET', KEYS[1]) == ARGV[1] then " +
                        "return redis.call('EXPIRE', KEYS[1], ARGV[2]) " +
                        "else return 0 end");
        return script;
    }

    private List<BookInfo> loadVisitScanBatch(long lastId, int batchSize) {
        QueryWrapper<BookInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.select(DatabaseConsts.CommonColumnEnum.ID.getName(), DatabaseConsts.BookTable.COLUMN_VISIT_COUNT)
                .eq("audit_status", 1)
                .gt(DatabaseConsts.BookTable.COLUMN_WORD_COUNT, 0)
                .gt(DatabaseConsts.CommonColumnEnum.ID.getName(), lastId)
                .orderByAsc(DatabaseConsts.CommonColumnEnum.ID.getName())
                .last("limit " + batchSize);
        return bookInfoMapper.selectList(queryWrapper);
    }

    private void appendBatchToBuildRank(String buildKey, List<BookInfo> batch) {
        List<Object> hashFields = batch.stream().map(v -> String.valueOf(v.getId())).collect(Collectors.toList());
        List<Object> bufferValues = stringRedisTemplate.opsForHash().multiGet(CacheConsts.BOOK_VISIT_COUNT_HASH, hashFields);
        List<Object> retryValues = stringRedisTemplate.opsForHash().multiGet(CacheConsts.BOOK_VISIT_COUNT_HASH + ":retry", hashFields);

        Set<ZSetOperations.TypedTuple<String>> tuples = new HashSet<>();
        for (int i = 0; i < batch.size(); i++) {
            BookInfo bookInfo = batch.get(i);
            long dbVisitCount = Objects.requireNonNullElse(bookInfo.getVisitCount(), 0L);
            long bufferIncrement = parseLongValue(bufferValues, i);
            long retryIncrement = parseLongValue(retryValues, i);
            double score = (double) (dbVisitCount + bufferIncrement + retryIncrement);
            if (score > 0) {
                tuples.add(new DefaultTypedTuple<>(String.valueOf(bookInfo.getId()), score));
            }
        }
        if (!tuples.isEmpty()) {
            stringRedisTemplate.opsForZSet().add(buildKey, tuples);
        }
    }

    private long parseLongValue(List<Object> values, int index) {
        if (CollectionUtils.isEmpty(values) || index >= values.size()) {
            return 0L;
        }
        Object value = values.get(index);
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private void mergeBuildRankToLive(String buildKey) {
        Set<ZSetOperations.TypedTuple<String>> buildTopSet = stringRedisTemplate.opsForZSet()
                .reverseRangeWithScores(buildKey, 0, CacheConsts.BOOK_VISIT_RANK_CANDIDATE_SIZE - 1);
        if (CollectionUtils.isEmpty(buildTopSet)) {
            return;
        }
        Set<ZSetOperations.TypedTuple<String>> mergedTuples = new HashSet<>();
        for (ZSetOperations.TypedTuple<String> tuple : buildTopSet) {
            String member = tuple.getValue();
            Double buildScore = tuple.getScore();
            if (member == null || buildScore == null) {
                continue;
            }
            Double liveScore = stringRedisTemplate.opsForZSet().score(CacheConsts.BOOK_VISIT_RANK_ZSET, member);
            double mergedScore = liveScore == null ? buildScore : Math.max(liveScore, buildScore);
            mergedTuples.add(new DefaultTypedTuple<>(member, mergedScore));
        }
        if (!mergedTuples.isEmpty()) {
            stringRedisTemplate.opsForZSet().add(CacheConsts.BOOK_VISIT_RANK_ZSET, mergedTuples);
        }
    }

    private void trimRankZSet(String key, int keepSize) {
        Long zsetSize = stringRedisTemplate.opsForZSet().size(key);
        if (zsetSize == null || zsetSize <= keepSize) {
            return;
        }
        long removeEnd = zsetSize - keepSize - 1;
        stringRedisTemplate.opsForZSet().removeRange(key, 0, removeEnd);
    }

    private void refreshVisitRankBookDetailCache() {
        Set<String> topBookIdSet = stringRedisTemplate.opsForZSet().reverseRange(
                CacheConsts.BOOK_VISIT_RANK_ZSET, 0, CacheConsts.BOOK_VISIT_RANK_CANDIDATE_SIZE - 1);
        if (CollectionUtils.isEmpty(topBookIdSet)) {
            return;
        }
        List<Long> topBookIds = topBookIdSet.stream().map(Long::valueOf).toList();
        List<BookInfo> bookInfos = bookInfoMapper.selectList(new QueryWrapper<BookInfo>()
                .in(DatabaseConsts.CommonColumnEnum.ID.getName(), topBookIds)
                .eq("audit_status", 1)
                .gt(DatabaseConsts.BookTable.COLUMN_WORD_COUNT, 0));
        if (CollectionUtils.isEmpty(bookInfos)) {
            return;
        }
        Map<Long, Integer> firstChapterMap = bookChapterMapper.selectFirstChapterNums(topBookIds)
                .stream().collect(Collectors.toMap(
                        m -> Long.valueOf(m.get("bookId").toString()),
                        m -> Integer.valueOf(m.get("firstChapterNum").toString()),
                        (v1, v2) -> v1));
        Map<String, Map<String, String>> allBookHashes = new HashMap<>();
        for (BookInfo book : bookInfos) {
            String hashKey = CacheConsts.BOOK_INFO_HASH_PREFIX + book.getId();
            allBookHashes.put(hashKey, buildBookInfoMap(book, firstChapterMap.getOrDefault(book.getId(), 1)));
        }
        batchWriteBookDetailHashes(allBookHashes);
    }

    private void batchWriteBookDetailHashes(Map<String, Map<String, String>> allBookHashes) {
        if (CollectionUtils.isEmpty(allBookHashes)) {
            return;
        }
        Set<String> existingHashes = new HashSet<>();
        for (String hashKey : allBookHashes.keySet()) {
            if (stringRedisTemplate.hasKey(hashKey)) {
                existingHashes.add(hashKey);
            }
        }
        stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            var serializer = stringRedisTemplate.getStringSerializer();
            allBookHashes.forEach((hashKey, mapData) -> {
                byte[] hashKeyBytes = serializer.serialize(hashKey);
                boolean hashExists = existingHashes.contains(hashKey);
                Map<byte[], byte[]> byteMap = new HashMap<>();
                if (hashExists) {
                    mapData.forEach((k, v) -> {
                        if (!"visitCount".equals(k)) {
                            byteMap.put(serializer.serialize(k), serializer.serialize(v));
                        }
                    });
                } else {
                    mapData.forEach((k, v) -> byteMap.put(serializer.serialize(k), serializer.serialize(v)));
                }
                if (!byteMap.isEmpty()) {
                    connection.hashCommands().hMSet(hashKeyBytes, byteMap);
                }
            });
            return null;
        });
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
                batchWriteBookDetailHashes(allBookHashes);
                log.info("{}缓存刷新完成，共 {} 本书籍详情已写入Redis", rankName, allBookHashes.size());
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