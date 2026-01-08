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
 * 频率：每5分钟执行一次
 * 
 * 注意：ES更新已分离到 BookVisitEsSyncJob，每小时批量更新一次，避免ES压力过大
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
     * - 每5分钟执行一次，将Redis中的访问量批量更新到数据库
     * - ES更新已分离到独立的定时任务（BookVisitEsSyncJob），每小时批量更新一次
     * - 这样可以避免高并发时ES压力过大导致服务宕机
     */
    @Scheduled(cron = "0 0/5 * * * ?")
    public void syncVisitCount() {
        String sourceKey = CacheConsts.BOOK_VISIT_COUNT_HASH;
        String tempKey = sourceKey + ":temp";

        if (!stringRedisTemplate.hasKey(sourceKey)) {
            return;
        }
        
        try {
            stringRedisTemplate.rename(sourceKey, tempKey);
        } catch (Exception e) {
            return;
        }

        Map<Object, Object> visitMap = stringRedisTemplate.opsForHash().entries(tempKey);
        if (visitMap.isEmpty()) {
            stringRedisTemplate.delete(tempKey);
            return;
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
            log.info("访问量同步完成，处理书籍数量：{}", updateList.size());
        } catch (Exception e) {
            log.error("批量更新访问量失败", e);
        }

        // 清理临时键
        stringRedisTemplate.delete(tempKey);
    }
}


//package com.novel.book.job;
//
//import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
//import com.novel.book.dao.entity.BookInfo;
//import com.novel.book.dao.mapper.BookInfoMapper;
//import com.novel.common.constant.AmqpConsts;
//import com.novel.common.constant.CacheConsts;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.apache.rocketmq.spring.core.RocketMQTemplate;
//import org.springframework.data.redis.core.StringRedisTemplate;
//import org.springframework.scheduling.annotation.Scheduled;
//import org.springframework.stereotype.Component;
//
//import java.util.Map;
//
///**
// * 小说点击量同步任务
// * 将 Redis 中的缓冲点击量批量写入数据库
// */
//@Component
//@RequiredArgsConstructor
//@Slf4j
//public class BookVisitSyncJob {
//
//    private final BookInfoMapper bookInfoMapper;
//    private final StringRedisTemplate stringRedisTemplate;
//    private final RocketMQTemplate rocketMQTemplate;
//
//    /**
//     * 每 1 分钟同步一次点击量到数据库
//     */
//    @Scheduled(cron = "0 0/1 * * * ?")
//    public void syncVisitCount() {
//        // 1. 定义临时 Key，防止在处理过程中丢失新写入的数据
//        String sourceKey = CacheConsts.BOOK_VISIT_COUNT_HASH;
//        String tempKey = sourceKey + ":temp";
//
//        // 2. 原子重命名 Key (如果没有数据，rename 会报错，所以先 check)
//        if (!stringRedisTemplate.hasKey(sourceKey)) {
//            return;
//        }
//        try {
//            stringRedisTemplate.rename(sourceKey, tempKey);
//        } catch (Exception e) {
//            // 可能刚刚被删除了，或者没有 key
//            return;
//        }
//
//        // 3. 读取临时 Key 的所有数据
//        Map<Object, Object> visitMap = stringRedisTemplate.opsForHash().entries(tempKey);
//        if (visitMap.isEmpty()) {
//            return;
//        }
//
//        log.info("开始同步小说点击量，共 {} 本书", visitMap.size());
//
//        // 4. 批量更新数据库 & 发送 MQ
//        for (Map.Entry<Object, Object> entry : visitMap.entrySet()) {
//            try {
//                Long bookId = Long.valueOf(entry.getKey().toString());
//                Integer visitCountToAdd = Integer.valueOf(entry.getValue().toString());
//
//                // 更新数据库: update book_info set visit_count = visit_count + ? where id = ?
//                // 使用 UpdateWrapper 实现递增
//                UpdateWrapper<BookInfo> updateWrapper = new UpdateWrapper<>();
//                updateWrapper.eq("id", bookId);
//                updateWrapper.setSql("visit_count = visit_count + " + visitCountToAdd);
//                bookInfoMapper.update(null, updateWrapper);
//
//                // 发送 MQ 通知 ES 更新 (因为 DB 变了，ES 也要变)
//                rocketMQTemplate.convertAndSend(AmqpConsts.BookChangeMq.TOPIC + ":" + AmqpConsts.BookChangeMq.TAG_UPDATE, bookId);
//
//            } catch (Exception e) {
//                log.error("同步书籍点击量失败，bookId={}", entry.getKey(), e);
//            }
//        }
//
//        // 5. 删除临时 Key
//        stringRedisTemplate.delete(tempKey);
//
//        log.info("小说点击量同步完成");
//    }
//}
//
