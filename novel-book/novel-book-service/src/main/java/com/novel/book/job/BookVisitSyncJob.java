package com.novel.book.job;
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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookVisitSyncJob {

    private final BookInfoMapper bookInfoMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final RocketMQTemplate rocketMQTemplate;

    @Scheduled(cron = "0 0/1 * * * ?")
    public void syncVisitCount() {
        String sourceKey = CacheConsts.BOOK_VISIT_COUNT_HASH;
        String tempKey = sourceKey + ":temp";

        if (!stringRedisTemplate.hasKey(sourceKey)) return;
        try {
            stringRedisTemplate.rename(sourceKey, tempKey);
        } catch (Exception e) { return; }

        Map<Object, Object> visitMap = stringRedisTemplate.opsForHash().entries(tempKey);
        if (visitMap.isEmpty()) return;

        // 1. 组装数据
        List<BookInfo> updateList = visitMap.entrySet().stream().map(entry -> {
            BookInfo book = new BookInfo();
            book.setId(Long.valueOf(entry.getKey().toString()));
            book.setVisitCount(Long.valueOf(entry.getValue().toString()));
            return book;
        }).collect(Collectors.toList());

        // 2. 批量更新数据库 (维持现状)
        bookInfoMapper.batchUpdateVisitCount(updateList);

        // 3. 核心优化：MQ 批量发送
        try {
            String destination = AmqpConsts.BookChangeMq.TOPIC + ":" + AmqpConsts.BookChangeMq.TAG_UPDATE;

            // 使用 RocketMQ 的 syncSend(Batch) 功能，发送批量消息
            // 消费者端无需修改，依然可以单条消费，但发送端减少了网络 IO
            List<Message<Long>> messages = updateList.stream()
                    .map(BookInfo::getId)
                    .map(id -> MessageBuilder.withPayload(id).build())
                    .collect(Collectors.toList());

            rocketMQTemplate.syncSend(destination, messages);

        } catch (Exception e) {
            log.error("MQ 批量发送失败", e);
        }

        // 4. 清理临时键
        stringRedisTemplate.delete(tempKey);
        log.info("同步完成，处理书籍数量：{}", updateList.size());
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
