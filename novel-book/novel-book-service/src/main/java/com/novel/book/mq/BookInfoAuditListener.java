package com.novel.book.mq;

import com.novel.book.dao.entity.BookInfo;
import com.novel.book.dao.mapper.BookInfoMapper;
import com.novel.book.dto.mq.BookAuditRequestMqDto;
import com.novel.common.constant.AmqpConsts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
    topic = AmqpConsts.BookAuditMq.TOPIC,
    selectorExpression = AmqpConsts.BookAuditMq.TAG_AUDIT_BOOK,
    consumerGroup = AmqpConsts.BookAuditMq.CONSUMER_GROUP_AUDIT_BOOK
)
public class BookInfoAuditListener implements RocketMQListener<Long> {

    private final BookInfoMapper bookInfoMapper;
    private final RocketMQTemplate rocketMQTemplate;

    @Override
    public void onMessage(Long bookId) {
        log.info("收到小说审核消息，bookId: {}", bookId);
        try {
            BookInfo bookInfo = bookInfoMapper.selectById(bookId);
            if (bookInfo == null) {
                log.warn("小说不存在，bookId: {}", bookId);
                return;
            }

            // 生成任务ID（用于关联审核请求和结果，保证幂等性）
            String taskId = generateTaskId(bookInfo.getId());

            // 构建审核请求MQ消息
            BookAuditRequestMqDto auditRequest = BookAuditRequestMqDto.builder()
                    .taskId(taskId)
                    .bookId(bookInfo.getId())
                    .bookName(bookInfo.getBookName())
                    .bookDesc(bookInfo.getBookDesc())
                    .build();

            // 发送审核请求到AI服务（异步处理）
            String destination = AmqpConsts.BookAuditRequestMq.TOPIC + ":" 
                    + AmqpConsts.BookAuditRequestMq.TAG_AUDIT_BOOK_REQUEST;
            rocketMQTemplate.convertAndSend(destination, auditRequest);
            log.info("书籍[{}]审核请求已发送到MQ，taskId: {}", bookInfo.getId(), taskId);

        } catch (Exception e) {
            log.error("处理书籍审核消息失败，bookId: {}", bookId, e);
        }
    }

    /**
     * 生成任务ID（用于关联审核请求和结果，保证幂等性）
     * @param bookId 书籍ID
     * @return 任务ID
     */
    private String generateTaskId(Long bookId) {
        return String.format("audit_book_%s_%s_%s", 
                bookId, 
                System.currentTimeMillis(), 
                UUID.randomUUID().toString().substring(0, 8));
    }
}

