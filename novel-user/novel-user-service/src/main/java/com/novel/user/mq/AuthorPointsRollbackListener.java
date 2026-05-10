package com.novel.user.mq;

import com.novel.common.constant.AmqpConsts;
import com.novel.user.dao.entity.AuthorPointsConsumeLog;
import com.novel.user.dao.mapper.AuthorPointsConsumeLogMapper;
import com.novel.user.dto.mq.AuthorPointsConsumeMqDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

/**
 * 作者积分回滚 MQ 消费者
 * 用于异步持久化积分回滚记录到数据库
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = AmqpConsts.AuthorPointsConsumeMq.TOPIC,
        consumerGroup = AmqpConsts.AuthorPointsConsumeMq.CONSUMER_GROUP_ROLLBACK,
        selectorExpression = AmqpConsts.AuthorPointsConsumeMq.TAG_ROLLBACK
)
public class AuthorPointsRollbackListener implements RocketMQListener<AuthorPointsConsumeMqDto> {

    private final AuthorPointsConsumeLogMapper logMapper;
    private final TransactionTemplate transactionTemplate;

    @Override
    public void onMessage(AuthorPointsConsumeMqDto dto) {
        log.info("收到作者积分回滚消息: {}", dto);

        try {
            Long authorId = dto.getAuthorId();

            transactionTemplate.executeWithoutResult(status -> persistRollback(dto, authorId));

            log.info("作者[{}]积分回滚记录已持久化，回滚点数: {}",
                    authorId, dto.getConsumePoints());

        } catch (Exception e) {
            log.error("持久化作者积分回滚记录失败: {}", dto, e);
            throw e;
        }
    }

    private void persistRollback(AuthorPointsConsumeMqDto dto, Long authorId) {
        try {
            AuthorPointsConsumeLog rollbackLog = new AuthorPointsConsumeLog();
            rollbackLog.setAuthorId(authorId);
            rollbackLog.setConsumeType(dto.getConsumeType());
            rollbackLog.setConsumePoints(dto.getConsumePoints());
            rollbackLog.setPointsType(0);
            rollbackLog.setRelatedId(dto.getRelatedId());
            rollbackLog.setRelatedDesc(dto.getRelatedDesc());
            rollbackLog.setConsumeDate(dto.getConsumeDate());
            rollbackLog.setCreateTime(LocalDateTime.now());
            rollbackLog.setUpdateTime(LocalDateTime.now());
            rollbackLog.setIdempotentKey(dto.getIdempotentKey());
            logMapper.insert(rollbackLog);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            log.warn("回滚日志已存在（幂等性拦截），作者ID: {}, Key: {}", authorId, dto.getIdempotentKey());
        }
    }
}
