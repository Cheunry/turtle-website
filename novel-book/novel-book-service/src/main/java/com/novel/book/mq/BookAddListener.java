package com.novel.book.mq;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.novel.book.dao.entity.BookInfo;
import com.novel.book.dao.mapper.BookInfoMapper;
import com.novel.book.dto.mq.BookAddMqDto;
import com.novel.common.constant.AmqpConsts;
import com.novel.common.constant.DatabaseConsts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;

/**
 * 书籍新增MQ消费者（处理书籍新增的所有数据库操作）
 * 将原本在网关同步链路中的数据库操作全部移到此处异步处理
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
    topic = AmqpConsts.BookAddMq.TOPIC,
    selectorExpression = AmqpConsts.BookAddMq.TAG_ADD,
    consumerGroup = AmqpConsts.BookAddMq.CONSUMER_GROUP_ADD
)
public class BookAddListener implements RocketMQListener<BookAddMqDto> {

    private final BookInfoMapper bookInfoMapper;
    private final RocketMQTemplate rocketMQTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onMessage(BookAddMqDto mqDto) {
        log.info("收到书籍新增MQ消息，bookName: {}, authorId: {}", mqDto.getBookName(), mqDto.getAuthorId());
        
        try {
            // 1. 校验小说名是否已存在
            QueryWrapper<BookInfo> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq(DatabaseConsts.BookTable.COLUMN_BOOK_NAME, mqDto.getBookName());
            if (bookInfoMapper.selectCount(queryWrapper) > 0) {
                log.warn("小说名已存在，忽略处理，bookName: {}", mqDto.getBookName());
                // 注意：这里不抛出异常，避免MQ消息重复消费
                // 可以考虑发送通知给用户
                return;
            }

            // 2. 构建并保存书籍信息
            BookInfo bookInfo = BookInfo.builder()
                    .workDirection(mqDto.getWorkDirection())
                    .categoryId(mqDto.getCategoryId())
                    .categoryName(mqDto.getCategoryName())
                    .picUrl(StringUtils.isBlank(mqDto.getPicUrl()) 
                            ? "https://turtle-website-1379089820.cos.ap-beijing.myqcloud.com/resource/2025/12/22/b1b7e29159423d4f0ab605a35245a3ed.png" 
                            : mqDto.getPicUrl())
                    .bookName(mqDto.getBookName())
                    .authorId(mqDto.getAuthorId())
                    .authorName(mqDto.getPenName())
                    .bookDesc(mqDto.getBookDesc())
                    .score(0)
                    .isVip(mqDto.getIsVip())
                    .auditStatus(mqDto.getAuditEnable() != null && mqDto.getAuditEnable() ? 0 : 1) // 根据审核开关决定初始状态
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .build();

            // 3. 保存小说信息
            bookInfoMapper.insert(bookInfo);
            log.debug("书籍信息新增完成，bookId: {}, bookName: {}", bookInfo.getId(), bookInfo.getBookName());

            // 4. 如果开启审核，触发AI审核
            if (mqDto.getAuditEnable() != null && mqDto.getAuditEnable()) {
                // 发送MQ异步审核
                if (TransactionSynchronizationManager.isSynchronizationActive()) {
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            try {
                                rocketMQTemplate.convertAndSend(
                                        AmqpConsts.BookAuditMq.TOPIC + ":" + AmqpConsts.BookAuditMq.TAG_AUDIT_BOOK, 
                                        bookInfo.getId());
                                log.debug("已发送书籍新增审核MQ，bookId: {}", bookInfo.getId());
                            } catch (Exception e) {
                                log.error("发送书籍新增审核MQ失败，bookId: {}", bookInfo.getId(), e);
                            }
                        }
                    });
                } else {
                    rocketMQTemplate.convertAndSend(
                            AmqpConsts.BookAuditMq.TOPIC + ":" + AmqpConsts.BookAuditMq.TAG_AUDIT_BOOK, 
                            bookInfo.getId());
                    log.debug("已发送书籍新增审核MQ，bookId: {}", bookInfo.getId());
                }
            }
        } catch (Exception e) {
            log.error("处理书籍新增MQ消息失败，bookName: {}", mqDto.getBookName(), e);
            // 不抛出异常，避免MQ消息重复消费
            // 可以考虑发送告警通知
        }
    }
}

