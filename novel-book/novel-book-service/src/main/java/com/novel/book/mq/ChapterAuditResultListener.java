package com.novel.book.mq;

import com.novel.book.dto.mq.ChapterAuditResultMqDto;
import com.novel.book.service.BookAuditService;
import com.novel.common.constant.AmqpConsts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 章节审核结果MQ消费者（业务服务）
 * 接收AI服务发送的审核结果，更新数据库
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
    topic = AmqpConsts.BookAuditResultMq.TOPIC,
    selectorExpression = AmqpConsts.BookAuditResultMq.TAG_AUDIT_CHAPTER_RESULT,
    consumerGroup = AmqpConsts.BookAuditResultMq.CONSUMER_GROUP_AUDIT_CHAPTER_RESULT
)
public class ChapterAuditResultListener implements RocketMQListener<ChapterAuditResultMqDto> {

    private final BookAuditService bookAuditService;

    @Override
    public void onMessage(ChapterAuditResultMqDto resultDto) {
        log.info("收到章节审核结果MQ消息，taskId: {}, chapterId: {}", 
                resultDto.getTaskId(), resultDto.getChapterId());
        
        try {
            bookAuditService.processChapterAuditResult(resultDto);
            log.debug("章节审核结果处理完成，taskId: {}, chapterId: {}", 
                    resultDto.getTaskId(), resultDto.getChapterId());
        } catch (Exception e) {
            log.error("处理章节审核结果失败，taskId: {}, chapterId: {}", 
                    resultDto.getTaskId(), resultDto.getChapterId(), e);
            // 注意：这里不抛出异常，避免MQ消息重复消费
            // 如果处理失败，可以记录日志或发送告警，后续可以通过定时任务补偿
        }
    }
}

