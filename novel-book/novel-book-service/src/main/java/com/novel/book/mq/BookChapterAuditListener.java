package com.novel.book.mq;

import com.novel.book.dao.entity.BookChapter;
import com.novel.book.dao.mapper.BookChapterMapper;
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
    selectorExpression = AmqpConsts.BookAuditMq.TAG_AUDIT_CHAPTER,
    consumerGroup = AmqpConsts.BookAuditMq.CONSUMER_GROUP_AUDIT_CHAPTER
)
public class BookChapterAuditListener implements RocketMQListener<Long> {

    private final BookAuditService bookAuditService;
    private final BookChapterMapper bookChapterMapper;

    @Override
    public void onMessage(Long chapterId) {
        log.info("收到章节审核消息，chapterId: {}", chapterId);
        try {
            BookChapter chapter = bookChapterMapper.selectById(chapterId);
            if (chapter != null) {
                bookAuditService.auditChapter(chapter);
            } else {
                log.warn("章节不存在，chapterId: {}", chapterId);
            }
        } catch (Exception e) {
            log.error("章节审核消费失败，chapterId: {}", chapterId, e);
        }
    }
}

