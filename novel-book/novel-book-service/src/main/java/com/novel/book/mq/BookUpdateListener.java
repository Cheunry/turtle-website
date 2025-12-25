package com.novel.book.mq;

import com.novel.book.dao.entity.BookInfo;
import com.novel.book.dao.mapper.BookInfoMapper;
import com.novel.book.dto.mq.BookUpdateMqDto;
import com.novel.common.constant.AmqpConsts;
import com.novel.common.constant.CacheConsts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.Objects;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;

/**
 * 书籍更新MQ消费者（处理书籍更新的所有数据库操作）
 * 将原本在网关同步链路中的数据库操作全部移到此处异步处理
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
    topic = AmqpConsts.BookUpdateMq.TOPIC,
    selectorExpression = AmqpConsts.BookUpdateMq.TAG_UPDATE,
    consumerGroup = AmqpConsts.BookUpdateMq.CONSUMER_GROUP_UPDATE
)
public class BookUpdateListener implements RocketMQListener<BookUpdateMqDto> {

    private final BookInfoMapper bookInfoMapper;
    private final RocketMQTemplate rocketMQTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onMessage(BookUpdateMqDto mqDto) {
        log.info("收到书籍更新MQ消息，bookId: {}, authorId: {}", mqDto.getBookId(), mqDto.getAuthorId());
        
        try {
            // 1. 权限校验（双重校验，确保安全）
            BookInfo bookInfo = bookInfoMapper.selectById(mqDto.getBookId());
            if (bookInfo == null) {
                log.warn("书籍不存在，忽略处理，bookId: {}", mqDto.getBookId());
                return;
            }
            if (!Objects.equals(bookInfo.getAuthorId(), mqDto.getAuthorId())) {
                log.warn("权限校验失败，书籍不属于该作者，bookId: {}, authorId: {}, submitAuthorId: {}", 
                        mqDto.getBookId(), bookInfo.getAuthorId(), mqDto.getAuthorId());
                return;
            }

            // 2. 更新信息
            BookInfo updateBook = new BookInfo();
            updateBook.setId(mqDto.getBookId());
            
            boolean hasUpdate = false;
            boolean needAudit = false; // 标记是否需要重新审核
            
            if (StringUtils.isNotBlank(mqDto.getBookName())) {
                updateBook.setBookName(mqDto.getBookName());
                hasUpdate = true;
                needAudit = true; // 小说名变更需要重新审核
            }
            if (StringUtils.isNotBlank(mqDto.getPicUrl())) {
                updateBook.setPicUrl(mqDto.getPicUrl());
                hasUpdate = true;
            }
            if (StringUtils.isNotBlank(mqDto.getBookDesc())) {
                updateBook.setBookDesc(mqDto.getBookDesc());
                hasUpdate = true;
                needAudit = true; // 简介变更需要重新审核
            }
            if (mqDto.getCategoryId() != null) {
                updateBook.setCategoryId(mqDto.getCategoryId());
                hasUpdate = true;
            }
            if (StringUtils.isNotBlank(mqDto.getCategoryName())) {
                updateBook.setCategoryName(mqDto.getCategoryName());
                hasUpdate = true;
            }
            if (mqDto.getWorkDirection() != null) {
                updateBook.setWorkDirection(mqDto.getWorkDirection());
                hasUpdate = true;
            }
            if (mqDto.getIsVip() != null) {
                updateBook.setIsVip(mqDto.getIsVip());
                hasUpdate = true;
            }
            if (mqDto.getBookStatus() != null) {
                updateBook.setBookStatus(mqDto.getBookStatus());
                hasUpdate = true;
            }

            if (hasUpdate) {
                // 如果小说名或简介有变更，且开启了审核，重置审核状态为待审核
                if (needAudit && mqDto.getAuditEnable() != null && mqDto.getAuditEnable()) {
                    updateBook.setAuditStatus(0);
                    updateBook.setAuditReason(null);
                }
                
                updateBook.setUpdateTime(LocalDateTime.now());
                bookInfoMapper.updateById(updateBook);
                
                log.debug("书籍信息更新完成，bookId: {}", mqDto.getBookId());
                
                // 清除 Redis 缓存，确保下次查询时获取最新数据
                try {
                    String cacheKey = CacheConsts.BOOK_INFO_HASH_PREFIX + mqDto.getBookId();
                    stringRedisTemplate.delete(cacheKey);
                    log.debug("已清除书籍信息缓存，bookId: {}, cacheKey: {}", mqDto.getBookId(), cacheKey);
                } catch (Exception e) {
                    log.warn("清除书籍信息缓存失败，bookId: {}, 不影响业务", mqDto.getBookId(), e);
                }
                
                // 如果小说名或简介有变更，且开启了审核，触发AI审核
                if (needAudit && mqDto.getAuditEnable() != null && mqDto.getAuditEnable()) {
                    // 发送MQ异步审核
                    if (TransactionSynchronizationManager.isSynchronizationActive()) {
                        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                            @Override
                            public void afterCommit() {
                                try {
                                    rocketMQTemplate.convertAndSend(
                                            AmqpConsts.BookAuditMq.TOPIC + ":" + AmqpConsts.BookAuditMq.TAG_AUDIT_BOOK, 
                                            mqDto.getBookId());
                                    log.debug("已发送书籍更新审核MQ，bookId: {}", mqDto.getBookId());
                                } catch (Exception e) {
                                    log.error("发送书籍更新审核MQ失败，bookId: {}", mqDto.getBookId(), e);
                                }
                            }
                        });
                    } else {
                        rocketMQTemplate.convertAndSend(
                                AmqpConsts.BookAuditMq.TOPIC + ":" + AmqpConsts.BookAuditMq.TAG_AUDIT_BOOK, 
                                mqDto.getBookId());
                        log.debug("已发送书籍更新审核MQ，bookId: {}", mqDto.getBookId());
                    }
                }
            } else {
                log.debug("没有需要更新的字段，bookId: {}", mqDto.getBookId());
            }
        } catch (Exception e) {
            log.error("处理书籍更新MQ消息失败，bookId: {}", mqDto.getBookId(), e);
            // 不抛出异常，避免MQ消息重复消费
            // 可以考虑发送告警通知
        }
    }
}

