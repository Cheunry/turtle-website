package com.novel.user.mq;

import com.novel.common.constant.AmqpConsts;
import com.novel.common.constant.CacheConsts;
import com.novel.user.dao.entity.AuthorInfo;
import com.novel.user.dao.entity.AuthorPointsConsumeLog;
import com.novel.user.dao.mapper.AuthorInfoMapper;
import com.novel.user.dao.mapper.AuthorPointsConsumeLogMapper;
import com.novel.user.dto.mq.AuthorPointsConsumeMqDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

/**
 * 作者积分消费 MQ 消费者
 * 用于异步持久化积分消费记录到数据库
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
    topic = AmqpConsts.AuthorPointsConsumeMq.TOPIC,
    consumerGroup = AmqpConsts.AuthorPointsConsumeMq.CONSUMER_GROUP_PERSIST,
    selectorExpression = AmqpConsts.AuthorPointsConsumeMq.TAG_DEDUCT
)
public class AuthorPointsConsumeListener implements RocketMQListener<AuthorPointsConsumeMqDto> {

    private final AuthorInfoMapper authorInfoMapper;
    private final AuthorPointsConsumeLogMapper logMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final TransactionTemplate transactionTemplate;

    @Override
    public void onMessage(AuthorPointsConsumeMqDto dto) {
        log.info("收到作者积分消费消息: {}", dto);

        try {
            Long authorId = dto.getAuthorId();

            String freeKey = String.format(CacheConsts.AUTHOR_FREE_POINTS_KEY, authorId);
            String paidKey = String.format(CacheConsts.AUTHOR_PAID_POINTS_KEY, authorId);

            String freeValue = stringRedisTemplate.opsForValue().get(freeKey);
            String paidValue = stringRedisTemplate.opsForValue().get(paidKey);

            int redisFree = parseRedisInt(freeValue, authorId, "免费");
            int redisPaid = parseRedisInt(paidValue, authorId, "付费");

            transactionTemplate.executeWithoutResult(status -> persistConsume(dto, authorId, redisFree, redisPaid));

            log.info("作者[{}]积分消费记录已持久化，免费: {}, 付费: {}",
                    authorId, dto.getUsedFreePoints(), dto.getUsedPaidPoints());

        } catch (Exception e) {
            log.error("持久化作者积分消费记录失败: {}", dto, e);
            throw e;
        }
    }

    private static int parseRedisInt(String value, Long authorId, String label) {
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            log.warn("解析 Redis {}积分失败，作者ID: {}, 值: {}", label, authorId, value);
            return 0;
        }
    }

    private void persistConsume(AuthorPointsConsumeMqDto dto, Long authorId, int redisFree, int redisPaid) {
        AuthorInfo author = authorInfoMapper.selectById(authorId);
        if (author != null) {
            int oldFree = author.getFreePoints() != null ? author.getFreePoints() : 0;
            int oldPaid = author.getPaidPoints() != null ? author.getPaidPoints() : 0;

            author.setFreePoints(redisFree);
            author.setPaidPoints(redisPaid);
            author.setUpdateTime(LocalDateTime.now());

            if (dto.getUsedFreePoints() > 0) {
                author.setFreePointsUpdateTime(LocalDateTime.now());
            }

            authorInfoMapper.updateById(author);
            log.debug("作者[{}]积分已从 Redis 同步到数据库，免费: {} -> {}, 付费: {} -> {}",
                    authorId, oldFree, redisFree, oldPaid, redisPaid);
        } else {
            log.warn("作者[{}]不存在，跳过积分更新", authorId);
        }

        if (dto.getUsedFreePoints() > 0) {
            try {
                AuthorPointsConsumeLog freeLog = new AuthorPointsConsumeLog();
                freeLog.setAuthorId(authorId);
                freeLog.setConsumeType(dto.getConsumeType());
                freeLog.setConsumePoints(dto.getUsedFreePoints());
                freeLog.setPointsType(0);
                freeLog.setRelatedId(dto.getRelatedId());
                freeLog.setRelatedDesc(dto.getRelatedDesc());
                freeLog.setConsumeDate(dto.getConsumeDate());
                freeLog.setCreateTime(LocalDateTime.now());
                freeLog.setUpdateTime(LocalDateTime.now());
                freeLog.setIdempotentKey(dto.getIdempotentKey() + "_FREE");
                logMapper.insert(freeLog);
            } catch (org.springframework.dao.DuplicateKeyException e) {
                log.warn("消费日志已存在（幂等性拦截），作者ID: {}, Key: {}_FREE", authorId, dto.getIdempotentKey());
            }
        }

        if (dto.getUsedPaidPoints() > 0) {
            try {
                AuthorPointsConsumeLog paidLog = new AuthorPointsConsumeLog();
                paidLog.setAuthorId(authorId);
                paidLog.setConsumeType(dto.getConsumeType());
                paidLog.setConsumePoints(dto.getUsedPaidPoints());
                paidLog.setPointsType(1);
                paidLog.setRelatedId(dto.getRelatedId());
                paidLog.setRelatedDesc(dto.getRelatedDesc());
                paidLog.setConsumeDate(dto.getConsumeDate());
                paidLog.setCreateTime(LocalDateTime.now());
                paidLog.setUpdateTime(LocalDateTime.now());
                paidLog.setIdempotentKey(dto.getIdempotentKey() + "_PAID");
                logMapper.insert(paidLog);
            } catch (org.springframework.dao.DuplicateKeyException e) {
                log.warn("消费日志已存在（幂等性拦截），作者ID: {}, Key: {}_PAID", authorId, dto.getIdempotentKey());
            }
        }
    }
}
