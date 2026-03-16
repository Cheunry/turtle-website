package com.novel.book.job;
import com.novel.book.dao.entity.BookInfo;
import com.novel.book.dao.mapper.BookInfoMapper;
import com.novel.common.constant.CacheConsts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 书籍访问量同步任务
 * 功能：将 Redis 中的访问量批量同步到数据库
 * 频率：每2分钟执行一次（优化：降低数据丢失风险）
 * 
 * 优化说明：
 * - 每2分钟执行一次，将Redis中的访问量批量更新到数据库
 * - 增加失败重试机制：同步失败时保留临时Key，下次重试，避免数据丢失
 * - ES更新已分离到独立的定时任务（BookVisitEsSyncJob），每小时批量更新一次
 * - 这样可以避免高并发时ES压力过大导致服务宕机
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BookVisitSyncJob {

    private final BookInfoMapper bookInfoMapper;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 同步访问量到数据库
     * 优化说明：
     * - 每2分钟执行一次，将Redis中的访问量批量更新到数据库
     * - 增加失败重试机制：同步失败时保留临时Key，下次重试
     * - ES更新已分离到独立的定时任务（BookVisitEsSyncJob），每小时批量更新一次
     */
    @Scheduled(cron = "0 0/2 * * * ?")
    public void syncVisitCount() {
        String sourceKey = CacheConsts.BOOK_VISIT_COUNT_HASH;
        String tempKey = sourceKey + ":temp";
        String retryKey = sourceKey + ":retry";

        // 1. 先处理上次失败的重试数据（如果存在）
        if (stringRedisTemplate.hasKey(retryKey)) {
            log.info("检测到上次同步失败的数据，开始重试...");
            boolean retrySuccess = processVisitCountSync(retryKey, true);
            if (retrySuccess) {
                log.info("重试数据同步成功");
            } else {
                log.warn("重试数据同步失败，将在下次任务时继续重试");
                return; // 重试失败，不处理新数据，避免数据堆积
            }
        }

        // 2. 处理新的访问量数据
        if (!stringRedisTemplate.hasKey(sourceKey)) {
            return;
        }
        
        try {
            // 原子重命名，防止处理过程中丢失新数据
            stringRedisTemplate.rename(sourceKey, tempKey);
        } catch (Exception e) {
            log.warn("重命名访问量缓冲Hash失败，可能已被其他实例处理", e);
            return;
        }

        // 3. 处理临时Key中的数据
        processVisitCountSync(tempKey, false);
    }

    /**
     * 处理访问量同步
     * @param key Redis Key（临时Key或重试Key）
     * @param isRetry 是否为重试数据
     * @return 是否同步成功
     */
    private boolean processVisitCountSync(String key, boolean isRetry) {
        Map<Object, Object> visitMap = stringRedisTemplate.opsForHash().entries(key);
        if (visitMap.isEmpty()) {
            // 数据为空，删除Key
            stringRedisTemplate.delete(key);
            return true;
        }

        // 组装数据
        List<BookInfo> updateList = visitMap.entrySet().stream().map(entry -> {
            BookInfo book = new BookInfo();
            book.setId(Long.valueOf(entry.getKey().toString()));
            book.setVisitCount(Long.valueOf(entry.getValue().toString()));
            return book;
        }).collect(Collectors.toList());

        // 批量更新数据库
        try {
            bookInfoMapper.batchUpdateVisitCount(updateList);
            log.info("访问量同步完成，处理书籍数量：{}，是否重试：{}", updateList.size(), isRetry);
            
            // 同步成功，删除临时Key
            stringRedisTemplate.delete(key);
            return true;
        } catch (Exception e) {
            log.error("批量更新访问量失败，书籍数量：{}，是否重试：{}", updateList.size(), isRetry, e);
            
            // 同步失败，保留数据用于重试
            if (!isRetry) {
                // 如果是新数据失败，重命名为重试Key
                try {
                    String retryKey = CacheConsts.BOOK_VISIT_COUNT_HASH + ":retry";
                    stringRedisTemplate.rename(key, retryKey);
                    log.warn("同步失败，数据已保存到重试Key，将在下次任务时重试");
                } catch (Exception renameException) {
                    log.error("重命名失败Key到重试Key失败，数据可能丢失", renameException);
                    // 如果重命名也失败，保留原Key，下次可能会重复处理，但不会丢失数据
                }
            } else {
                // 如果是重试数据失败，保留原Key，下次继续重试
                log.warn("重试数据同步失败，保留重试Key，将在下次任务时继续重试");
            }
            return false;
        }
    }
}