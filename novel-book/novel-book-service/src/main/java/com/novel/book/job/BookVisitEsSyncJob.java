package com.novel.book.job;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.novel.book.dao.entity.BookInfo;
import com.novel.book.dao.mapper.BookInfoMapper;
import com.novel.common.constant.AmqpConsts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 书籍访问量ES同步任务
 * 功能：批量更新ES中的书籍访问量数据
 * 频率：每小时执行一次（整点执行）
 * 
 * 优化说明：
 * - 将ES更新从高频的数据库同步任务中分离出来
 * - 每小时批量更新一次，避免高并发时ES压力过大导致服务宕机
 * - 只更新审核通过的书籍（auditStatus = 1）
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BookVisitEsSyncJob {

    private final BookInfoMapper bookInfoMapper;
    private final RocketMQTemplate rocketMQTemplate;

    /**
     * 批量更新ES中的书籍访问量
     * 执行时间：每小时整点执行（例如：00:00, 01:00, 02:00...）
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void syncVisitCountToEs() {
        log.info("开始批量更新ES中的书籍访问量...");
        
        try {
            // 1. 查询所有审核通过的书籍（只查询ID，减少内存占用）
            QueryWrapper<BookInfo> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("audit_status", 1) // 只查询审核通过的书籍
                       .select("id"); // 只查询ID字段，减少内存占用
            
            List<BookInfo> bookList = bookInfoMapper.selectList(queryWrapper);
            
            if (bookList.isEmpty()) {
                log.info("没有需要更新ES的书籍");
                return;
            }
            
            // 2. 提取书籍ID列表
            List<Long> bookIds = bookList.stream()
                    .map(BookInfo::getId)
                    .toList();
            
            log.info("准备更新ES，书籍数量：{}", bookIds.size());
            
            // 3. 批量发送MQ消息更新ES
            // 使用批量发送，减少网络IO
            String destination = AmqpConsts.BookChangeMq.TOPIC + ":" + AmqpConsts.BookChangeMq.TAG_UPDATE;
            
            // 分批处理，每批1000条，避免单次发送过多消息
            int batchSize = 1000;
            int totalBatches = (bookIds.size() + batchSize - 1) / batchSize;
            
            for (int i = 0; i < totalBatches; i++) {
                int start = i * batchSize;
                int end = Math.min(start + batchSize, bookIds.size());
                List<Long> batchIds = bookIds.subList(start, end);
                
                try {
                    // 构建批量消息
                    List<Message<Long>> messages = batchIds.stream()
                            .map(id -> MessageBuilder.withPayload(id).build())
                            .collect(Collectors.toList());
                    
                    // 批量发送
                    rocketMQTemplate.syncSend(destination, messages);
                    log.debug("已发送第 {}/{} 批ES更新消息，本批数量：{}", 
                            i + 1, totalBatches, batchIds.size());
                } catch (Exception e) {
                    log.error("批量发送ES更新消息失败，批次：{}/{}, 书籍数量：{}", 
                            i + 1, totalBatches, batchIds.size(), e);
                    // 继续处理下一批，不中断整个任务
                }
            }
            
            log.info("ES访问量批量更新任务完成，共处理 {} 本书籍，分 {} 批发送", 
                    bookIds.size(), totalBatches);
            
        } catch (Exception e) {
            log.error("批量更新ES访问量失败", e);
        }
    }
}
