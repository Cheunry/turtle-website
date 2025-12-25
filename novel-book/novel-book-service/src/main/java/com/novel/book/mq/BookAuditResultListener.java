package com.novel.book.mq;

import com.novel.book.dto.mq.BookAuditResultMqDto;
import com.novel.book.service.BookAuditService;
import com.novel.common.constant.AmqpConsts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 书籍审核结果MQ消费者（业务服务）
 * 接收AI服务发送的审核结果，更新数据库
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
    topic = AmqpConsts.BookAuditResultMq.TOPIC,
    selectorExpression = AmqpConsts.BookAuditResultMq.TAG_AUDIT_BOOK_RESULT,
    consumerGroup = AmqpConsts.BookAuditResultMq.CONSUMER_GROUP_AUDIT_BOOK_RESULT
)
public class BookAuditResultListener implements RocketMQListener<BookAuditResultMqDto> {

    private final BookAuditService bookAuditService;

    @Override
    public void onMessage(BookAuditResultMqDto resultDto) {
        log.info("收到书籍审核结果MQ消息，taskId: {}, bookId: {}", 
                resultDto.getTaskId(), resultDto.getBookId());
        
        try {
            bookAuditService.processBookAuditResult(resultDto);
            log.debug("书籍审核结果处理完成，taskId: {}, bookId: {}", 
                    resultDto.getTaskId(), resultDto.getBookId());
        } catch (Exception e) {
            log.error("处理书籍审核结果失败，taskId: {}, bookId: {}", 
                    resultDto.getTaskId(), resultDto.getBookId(), e);
            // 注意：这里不抛出异常，避免MQ消息重复消费
            // 如果处理失败，可以记录日志或发送告警，后续可以通过定时任务补偿
        }
    }
}

