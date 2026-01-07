package com.novel.book.mq;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.novel.book.dao.entity.BookInfo;
import com.novel.book.dao.mapper.BookInfoMapper;
import com.novel.book.dto.mq.BookAuditRequestMqDto;
import com.novel.book.dto.mq.BookSubmitMqDto;
import com.novel.common.constant.AmqpConsts;
import com.novel.common.constant.CacheConsts;
import com.novel.common.constant.DatabaseConsts;
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
import java.util.UUID;

/**
 * 书籍提交MQ消费者（统一处理书籍新增和更新的所有数据库操作）
 * 替代原有的 BookAddListener, BookUpdateListener 和 BookInfoAuditListener
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
    topic = AmqpConsts.BookSubmitMq.TOPIC,
    selectorExpression = AmqpConsts.BookSubmitMq.TAG_SUBMIT,
    consumerGroup = AmqpConsts.BookSubmitMq.CONSUMER_GROUP_SUBMIT
)
public class BookSubmitListener implements RocketMQListener<BookSubmitMqDto> {

    private final BookInfoMapper bookInfoMapper;
    private final RocketMQTemplate rocketMQTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onMessage(BookSubmitMqDto submitDto) {
        log.info("收到书籍提交MQ消息，bookId: {}, operationType: {}, bookName: {}", 
                submitDto.getBookId(), submitDto.getOperationType(), submitDto.getBookName());
        
        try {
            if ("ADD".equals(submitDto.getOperationType())) {
                handleBookAdd(submitDto);
            } else if ("UPDATE".equals(submitDto.getOperationType())) {
                handleBookUpdate(submitDto);
            } else {
                log.warn("未知的操作类型，忽略处理，operationType: {}", submitDto.getOperationType());
            }
        } catch (Exception e) {
            log.error("处理书籍提交MQ消息失败，bookId: {}, operationType: {}", 
                    submitDto.getBookId(), submitDto.getOperationType(), e);
            // 不抛出异常，避免MQ消息重复消费
        }
    }

    /**
     * 处理书籍新增
     */
    private void handleBookAdd(BookSubmitMqDto submitDto) {
        // 1. 校验小说名是否已存在
        QueryWrapper<BookInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(DatabaseConsts.BookTable.COLUMN_BOOK_NAME, submitDto.getBookName());
        if (bookInfoMapper.selectCount(queryWrapper) > 0) {
            log.warn("小说名已存在，忽略处理，bookName: {}", submitDto.getBookName());
            return;
        }

        // 2. 构建并保存书籍信息
        BookInfo bookInfo = BookInfo.builder()
                .workDirection(submitDto.getWorkDirection())
                .categoryId(submitDto.getCategoryId())
                .categoryName(submitDto.getCategoryName())
                .picUrl(StringUtils.isBlank(submitDto.getPicUrl()) 
                        ? "https://turtle-website-1379089820.cos.ap-beijing.myqcloud.com/resource/2025/12/22/b1b7e29159423d4f0ab605a35245a3ed.png" 
                        : submitDto.getPicUrl())
                .bookName(submitDto.getBookName())
                .authorId(submitDto.getAuthorId())
                .authorName(submitDto.getPenName())
                .bookDesc(submitDto.getBookDesc())
                .score(0)
                .isVip(submitDto.getIsVip())
                .auditStatus(Boolean.TRUE.equals(submitDto.getAuditEnable()) ? 0 : 1) // 根据审核开关决定初始状态
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        // 3. 保存小说信息
        bookInfoMapper.insert(bookInfo);
        log.debug("书籍信息新增完成，bookId: {}, bookName: {}", bookInfo.getId(), bookInfo.getBookName());

        // 4. 如果开启审核，触发AI审核
        if (Boolean.TRUE.equals(submitDto.getAuditEnable())) {
            sendAuditRequest(bookInfo);
        }
    }

    /**
     * 处理书籍更新
     */
    private void handleBookUpdate(BookSubmitMqDto submitDto) {
        // 1. 权限校验
        BookInfo bookInfo = bookInfoMapper.selectById(submitDto.getBookId());
        if (bookInfo == null) {
            log.warn("书籍不存在，忽略处理，bookId: {}", submitDto.getBookId());
            return;
        }
        if (!Objects.equals(bookInfo.getAuthorId(), submitDto.getAuthorId())) {
            log.warn("权限校验失败，书籍不属于该作者，bookId: {}, authorId: {}, submitAuthorId: {}", 
                    submitDto.getBookId(), bookInfo.getAuthorId(), submitDto.getAuthorId());
            return;
        }

        // 2. 更新信息
        BookInfo updateBook = new BookInfo();
        updateBook.setId(submitDto.getBookId());
        
        boolean hasUpdate = false;
        boolean needAudit = false; // 标记是否需要重新审核
        
        if (StringUtils.isNotBlank(submitDto.getBookName())) {
            updateBook.setBookName(submitDto.getBookName());
            hasUpdate = true;
            needAudit = true; // 小说名变更需要重新审核
        }
        if (StringUtils.isNotBlank(submitDto.getPicUrl())) {
            updateBook.setPicUrl(submitDto.getPicUrl());
            hasUpdate = true;
        }
        if (StringUtils.isNotBlank(submitDto.getBookDesc())) {
            updateBook.setBookDesc(submitDto.getBookDesc());
            hasUpdate = true;
            needAudit = true; // 简介变更需要重新审核
        }
        if (submitDto.getCategoryId() != null) {
            updateBook.setCategoryId(submitDto.getCategoryId());
            hasUpdate = true;
        }
        if (StringUtils.isNotBlank(submitDto.getCategoryName())) {
            updateBook.setCategoryName(submitDto.getCategoryName());
            hasUpdate = true;
        }
        if (submitDto.getWorkDirection() != null) {
            updateBook.setWorkDirection(submitDto.getWorkDirection());
            hasUpdate = true;
        }
        if (submitDto.getIsVip() != null) {
            updateBook.setIsVip(submitDto.getIsVip());
            hasUpdate = true;
        }
        if (submitDto.getBookStatus() != null) {
            updateBook.setBookStatus(submitDto.getBookStatus());
            hasUpdate = true;
        }

        if (hasUpdate) {
            // 如果小说名或简介有变更，且开启了审核，重置审核状态为待审核
            if (needAudit && Boolean.TRUE.equals(submitDto.getAuditEnable())) {
                updateBook.setAuditStatus(0);
                updateBook.setAuditReason(null);
            }
            
            updateBook.setUpdateTime(LocalDateTime.now());
            bookInfoMapper.updateById(updateBook);
            
            log.debug("书籍信息更新完成，bookId: {}", submitDto.getBookId());
            
            // 清除 Redis 缓存
            try {
                String cacheKey = CacheConsts.BOOK_INFO_HASH_PREFIX + submitDto.getBookId();
                stringRedisTemplate.delete(cacheKey);
                log.debug("已清除书籍信息缓存，bookId: {}, cacheKey: {}", submitDto.getBookId(), cacheKey);
            } catch (Exception e) {
                log.warn("清除书籍信息缓存失败，bookId: {}, 不影响业务", submitDto.getBookId(), e);
            }
            
            // 如果小说名或简介有变更，且开启了审核，触发AI审核
            if (needAudit && Boolean.TRUE.equals(submitDto.getAuditEnable())) {
                // 需要发送最新的书籍信息进行审核
                // 这里重新查询一次或者合并 updateBook 和 bookInfo 都可以
                // 为保险起见，使用 updateBook 中的关键信息（如果有）和原 bookInfo 中的信息
                BookInfo auditBookInfo = new BookInfo();
                auditBookInfo.setId(submitDto.getBookId());
                auditBookInfo.setBookName(StringUtils.isNotBlank(submitDto.getBookName()) ? submitDto.getBookName() : bookInfo.getBookName());
                auditBookInfo.setBookDesc(StringUtils.isNotBlank(submitDto.getBookDesc()) ? submitDto.getBookDesc() : bookInfo.getBookDesc());
                
                sendAuditRequest(auditBookInfo);
            }
        } else {
            log.debug("没有需要更新的字段，bookId: {}", submitDto.getBookId());
        }
    }

    /**
     * 发送审核请求到AI服务
     */
    private void sendAuditRequest(BookInfo bookInfo) {
        String taskId = generateTaskId(bookInfo.getId());
        
        BookAuditRequestMqDto auditRequest = BookAuditRequestMqDto.builder()
                .taskId(taskId)
                .bookId(bookInfo.getId())
                .bookName(bookInfo.getBookName())
                .bookDesc(bookInfo.getBookDesc())
                .build();

        String destination = AmqpConsts.BookAuditRequestMq.TOPIC + ":" 
                + AmqpConsts.BookAuditRequestMq.TAG_AUDIT_BOOK_REQUEST;

        // 在事务提交后发送 MQ 消息
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        rocketMQTemplate.convertAndSend(destination, auditRequest);
                        log.info("书籍[{}]审核请求已发送到MQ，taskId: {}", bookInfo.getId(), taskId);
                    } catch (Exception e) {
                        log.error("发送书籍审核请求MQ失败，bookId: {}", bookInfo.getId(), e);
                    }
                }
            });
        } else {
            try {
                rocketMQTemplate.convertAndSend(destination, auditRequest);
                log.info("书籍[{}]审核请求已发送到MQ，taskId: {}", bookInfo.getId(), taskId);
            } catch (Exception e) {
                log.error("发送书籍审核请求MQ失败，bookId: {}", bookInfo.getId(), e);
            }
        }
    }

    /**
     * 生成任务ID
     */
    private String generateTaskId(Long bookId) {
        return String.format("audit_book_%s_%s_%s", 
                bookId, 
                System.currentTimeMillis(), 
                UUID.randomUUID().toString().substring(0, 8));
    }
}

