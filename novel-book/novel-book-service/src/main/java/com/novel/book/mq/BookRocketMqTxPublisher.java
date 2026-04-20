package com.novel.book.mq;

import com.novel.book.dto.req.BookDelReqDto;
import com.novel.book.dto.req.ChapterDelReqDto;
import com.novel.common.constant.AmqpConsts;
import com.novel.common.constant.ErrorCodeEnum;
import com.novel.common.resp.RestResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.LocalTransactionState;
import org.apache.rocketmq.client.producer.TransactionSendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * RocketMQ 事务消息发送封装：删除类操作与 book-change 通知同事务；审核/通知类在 DB 提交后投递。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookRocketMqTxPublisher {

    private final RocketMQTemplate rocketMQTemplate;

    /**
     * 删除章节：本地删库与 topic-book-change 半消息同事务提交。
     */
    public RestResp<Void> sendDeleteChapterInTransaction(ChapterDelReqDto dto) {
        String destination = AmqpConsts.BookChangeMq.TOPIC + ":" + AmqpConsts.BookChangeMq.TAG_UPDATE;
        try {
            TransactionSendResult result = rocketMQTemplate.sendMessageInTransaction(
                    destination,
                    MessageBuilder.withPayload(dto.getBookId()).build(),
                    dto);
            return toRestResp(result, "删除章节");
        } catch (Exception e) {
            log.error("事务消息发送失败（删除章节）, bookId={}", dto.getBookId(), e);
            return RestResp.fail(ErrorCodeEnum.SYSTEM_ERROR);
        }
    }

    /**
     * 删除书籍：本地删库与 topic-book-change 半消息同事务提交。
     */
    public RestResp<Void> sendDeleteBookInTransaction(BookDelReqDto dto) {
        String destination = AmqpConsts.BookChangeMq.TOPIC + ":" + AmqpConsts.BookChangeMq.TAG_UPDATE;
        try {
            TransactionSendResult result = rocketMQTemplate.sendMessageInTransaction(
                    destination,
                    MessageBuilder.withPayload(dto.getBookId()).build(),
                    dto);
            return toRestResp(result, "删除书籍");
        } catch (Exception e) {
            log.error("事务消息发送失败（删除书籍）, bookId={}", dto.getBookId(), e);
            return RestResp.fail(ErrorCodeEnum.SYSTEM_ERROR);
        }
    }

    /**
     * 在 Spring 事务成功提交后，以事务消息投递（executeLocalTransaction 直接提交半消息，回查用于 Broker 不确定状态）。
     */
    public void sendAfterLocalDbCommit(String destination, Object payload) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendTransactionalNoLocalWork(destination, payload);
                }
            });
        } else {
            sendTransactionalNoLocalWork(destination, payload);
        }
    }

    private void sendTransactionalNoLocalWork(String destination, Object payload) {
        try {
            rocketMQTemplate.sendMessageInTransaction(
                    destination,
                    MessageBuilder.withPayload(payload).build(),
                    BookRocketMqAfterCommitMarker.INSTANCE);
        } catch (Exception e) {
            log.error("事务消息投递失败 destination={}", destination, e);
        }
    }

    private static RestResp<Void> toRestResp(TransactionSendResult result, String action) {
        if (result == null || result.getLocalTransactionState() == null) {
            return RestResp.fail(ErrorCodeEnum.SYSTEM_ERROR, action + "结果未知");
        }
        return switch (result.getLocalTransactionState()) {
            case COMMIT_MESSAGE -> RestResp.ok();
            case ROLLBACK_MESSAGE -> RestResp.fail(ErrorCodeEnum.USER_REQUEST_PARAM_ERROR, action + "未生效");
            case UNKNOW -> RestResp.fail(ErrorCodeEnum.SYSTEM_ERROR, action + "状态未决，请稍后重试");
        };
    }
}
