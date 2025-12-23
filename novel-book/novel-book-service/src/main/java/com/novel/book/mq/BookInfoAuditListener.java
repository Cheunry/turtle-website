package com.novel.book.mq;

import com.novel.book.dao.entity.BookInfo;
import com.novel.book.dao.mapper.BookInfoMapper;
import com.novel.book.service.BookAuditService;
import com.novel.common.constant.AmqpConsts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
    topic = AmqpConsts.BookAuditMq.TOPIC,
    selectorExpression = AmqpConsts.BookAuditMq.TAG_AUDIT_BOOK,
    consumerGroup = AmqpConsts.BookAuditMq.CONSUMER_GROUP_AUDIT_BOOK
)
public class BookInfoAuditListener implements RocketMQListener<Long> {

    private final BookAuditService bookAuditService;
    private final BookInfoMapper bookInfoMapper;

    @Override
    public void onMessage(Long bookId) {
        log.info("收到小说审核消息，bookId: {}", bookId);
        try {
            BookInfo bookInfo = bookInfoMapper.selectById(bookId);
            if (bookInfo != null) {
                bookAuditService.auditBookInfo(bookInfo);
            } else {
                log.warn("小说不存在，bookId: {}", bookId);
            }
        } catch (Exception e) {
            log.error("小说审核消费失败，bookId: {}", bookId, e);
        }
    }
}

