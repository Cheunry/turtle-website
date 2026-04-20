package com.novel.book.mq;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.book.dao.entity.ContentAudit;
import com.novel.book.dao.mapper.ContentAuditMapper;
import com.novel.book.dto.mq.BookAuditRequestMqDto;
import com.novel.book.dto.mq.ChapterAuditRequestMqDto;
import com.novel.book.dto.req.BookDelReqDto;
import com.novel.book.dto.req.ChapterDelReqDto;
import com.novel.common.constant.AmqpConsts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionState;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 与默认 {@link RocketMQTemplate} 绑定的事务监听器（生产者组取自 {@code rocketmq.producer.group}）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQTransactionListener(rocketMQTemplateBeanName = "rocketMQTemplate")
@SuppressWarnings("rawtypes")
public class BookRocketMqTransactionListener implements RocketMQLocalTransactionListener {

    private static final int CONTENT_AUDIT_SOURCE_BOOK = 0;
    private static final int CONTENT_AUDIT_SOURCE_CHAPTER = 1;
    private static final int CONTENT_AUDIT_STATUS_PENDING = 0;

    private final BookLocalTxExecutor bookLocalTxExecutor;
    private final ContentAuditMapper contentAuditMapper;
    private final ObjectMapper objectMapper;

    @Override
    public RocketMQLocalTransactionState executeLocalTransaction(org.springframework.messaging.Message msg, Object arg) {
        if (arg == BookRocketMqAfterCommitMarker.INSTANCE) {
            return RocketMQLocalTransactionState.COMMIT;
        }
        if (arg instanceof ChapterDelReqDto) {
            try {
                bookLocalTxExecutor.deleteBookChapter((ChapterDelReqDto) arg);
                return RocketMQLocalTransactionState.COMMIT;
            } catch (BookLocalTxAbortException e) {
                log.info("事务消息回滚（删除章节）: {}", e.getMessage());
                return RocketMQLocalTransactionState.ROLLBACK;
            } catch (Exception e) {
                log.error("事务消息本地执行异常（删除章节）", e);
                return RocketMQLocalTransactionState.ROLLBACK;
            }
        }
        if (arg instanceof BookDelReqDto) {
            try {
                bookLocalTxExecutor.deleteBook((BookDelReqDto) arg);
                return RocketMQLocalTransactionState.COMMIT;
            } catch (Exception e) {
                log.error("事务消息本地执行异常（删除书籍）", e);
                return RocketMQLocalTransactionState.ROLLBACK;
            }
        }
        log.warn("未知的事务消息参数类型: {}", arg == null ? "null" : arg.getClass().getName());
        return RocketMQLocalTransactionState.ROLLBACK;
    }

    @Override
    public RocketMQLocalTransactionState checkLocalTransaction(org.springframework.messaging.Message msg) {
        String topic = header(msg, RocketMQHeaders.TOPIC);
        String tags = header(msg, RocketMQHeaders.TAGS);
        try {
            if (AmqpConsts.BookAuditRequestMq.TOPIC.equals(topic)
                    && AmqpConsts.BookAuditRequestMq.TAG_AUDIT_BOOK_REQUEST.equals(tags)) {
                BookAuditRequestMqDto dto = readPayload(msg, BookAuditRequestMqDto.class);
                if (dto != null && dto.getBookId() != null && hasPendingAudit(CONTENT_AUDIT_SOURCE_BOOK, dto.getBookId())) {
                    return RocketMQLocalTransactionState.COMMIT;
                }
                return RocketMQLocalTransactionState.ROLLBACK;
            }
            if (AmqpConsts.BookAuditRequestMq.TOPIC.equals(topic)
                    && AmqpConsts.BookAuditRequestMq.TAG_AUDIT_CHAPTER_REQUEST.equals(tags)) {
                ChapterAuditRequestMqDto dto = readPayload(msg, ChapterAuditRequestMqDto.class);
                if (dto != null && dto.getChapterId() != null
                        && hasPendingAudit(CONTENT_AUDIT_SOURCE_CHAPTER, dto.getChapterId())) {
                    return RocketMQLocalTransactionState.COMMIT;
                }
                return RocketMQLocalTransactionState.ROLLBACK;
            }
        } catch (Exception e) {
            log.warn("checkLocalTransaction 解析失败 topic={} tags={}", topic, tags, e);
            return RocketMQLocalTransactionState.UNKNOWN;
        }
        return RocketMQLocalTransactionState.COMMIT;
    }

    private static String header(org.springframework.messaging.Message msg, String name) {
        String key = RocketMQHeaders.PREFIX + name;
        String v = msg.getHeaders().get(key, String.class);
        return v != null ? v : msg.getHeaders().get(name, String.class);
    }

    private <T> T readPayload(org.springframework.messaging.Message msg, Class<T> type) throws java.io.IOException {
        Object payload = msg.getPayload();
        if (payload == null) {
            return null;
        }
        if (type.isInstance(payload)) {
            return type.cast(payload);
        }
        if (payload instanceof byte[] b) {
            return objectMapper.readValue(b, type);
        }
        if (payload instanceof String s) {
            return objectMapper.readValue(s.getBytes(StandardCharsets.UTF_8), type);
        }
        return objectMapper.convertValue(payload, type);
    }

    private boolean hasPendingAudit(int sourceType, Long sourceId) {
        QueryWrapper<ContentAudit> w = new QueryWrapper<>();
        w.eq("source_type", sourceType)
                .eq("source_id", sourceId)
                .eq("audit_status", CONTENT_AUDIT_STATUS_PENDING)
                .last("LIMIT 1");
        return contentAuditMapper.selectCount(w) > 0;
    }
}
