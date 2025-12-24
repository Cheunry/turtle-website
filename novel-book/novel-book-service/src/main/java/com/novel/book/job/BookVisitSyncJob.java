package com.novel.book.job;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.novel.book.dao.entity.BookInfo;
import com.novel.book.dao.mapper.BookInfoMapper;
import com.novel.common.constant.AmqpConsts;
import com.novel.common.constant.CacheConsts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 小说点击量同步任务
 * 将 Redis 中的缓冲点击量批量写入数据库
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BookVisitSyncJob {

    private final BookInfoMapper bookInfoMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final RocketMQTemplate rocketMQTemplate;

    /**
     * 每 1 分钟同步一次点击量到数据库
     */
    @Scheduled(cron = "0 0/1 * * * ?")
    public void syncVisitCount() {
        // 1. 定义临时 Key，防止在处理过程中丢失新写入的数据
        String sourceKey = CacheConsts.BOOK_VISIT_COUNT_HASH;
        String tempKey = sourceKey + ":temp";

        // 2. 原子重命名 Key (如果没有数据，rename 会报错，所以先 check)
        if (!stringRedisTemplate.hasKey(sourceKey)) {
            return;
        }
        try {
            stringRedisTemplate.rename(sourceKey, tempKey);
        } catch (Exception e) {
            // 可能刚刚被删除了，或者没有 key
            return;
        }

        // 3. 读取临时 Key 的所有数据
        Map<Object, Object> visitMap = stringRedisTemplate.opsForHash().entries(tempKey);
        if (visitMap.isEmpty()) {
            return;
        }

        log.info("开始同步小说点击量，共 {} 本书", visitMap.size());

        // 4. 批量更新数据库 & 发送 MQ
        for (Map.Entry<Object, Object> entry : visitMap.entrySet()) {
            try {
                Long bookId = Long.valueOf(entry.getKey().toString());
                Integer visitCountToAdd = Integer.valueOf(entry.getValue().toString());

                // 更新数据库: update book_info set visit_count = visit_count + ? where id = ?
                // 使用 UpdateWrapper 实现递增
                UpdateWrapper<BookInfo> updateWrapper = new UpdateWrapper<>();
                updateWrapper.eq("id", bookId);
                updateWrapper.setSql("visit_count = visit_count + " + visitCountToAdd);
                bookInfoMapper.update(null, updateWrapper);

                // 发送 MQ 通知 ES 更新 (因为 DB 变了，ES 也要变)
                rocketMQTemplate.convertAndSend(AmqpConsts.BookChangeMq.TOPIC + ":" + AmqpConsts.BookChangeMq.TAG_UPDATE, bookId);

            } catch (Exception e) {
                log.error("同步书籍点击量失败，bookId={}", entry.getKey(), e);
            }
        }

        // 5. 删除临时 Key
        stringRedisTemplate.delete(tempKey);
        
        log.info("小说点击量同步完成");
    }
}

