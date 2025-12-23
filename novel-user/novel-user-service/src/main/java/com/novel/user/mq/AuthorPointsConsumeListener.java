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
import org.springframework.transaction.annotation.Transactional;

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onMessage(AuthorPointsConsumeMqDto dto) {
        log.info("收到作者积分消费消息: {}", dto);
        
        try {
            Long authorId = dto.getAuthorId();
            
            // 1. 从 Redis 读取最新积分值，同步到数据库
            // 这样可以保证最终一致性，Redis 是数据源
            String freeKey = String.format(CacheConsts.AUTHOR_FREE_POINTS_KEY, authorId);
            String paidKey = String.format(CacheConsts.AUTHOR_PAID_POINTS_KEY, authorId);
            
            String freeValue = stringRedisTemplate.opsForValue().get(freeKey);
            String paidValue = stringRedisTemplate.opsForValue().get(paidKey);
            
            int redisFree = 0;
            int redisPaid = 0;
            
            if (freeValue != null) {
                try {
                    redisFree = Integer.parseInt(freeValue);
                } catch (NumberFormatException e) {
                    log.warn("解析 Redis 免费积分失败，作者ID: {}, 值: {}", authorId, freeValue);
                }
            }
            
            if (paidValue != null) {
                try {
                    redisPaid = Integer.parseInt(paidValue);
                } catch (NumberFormatException e) {
                    log.warn("解析 Redis 付费积分失败，作者ID: {}, 值: {}", authorId, paidValue);
                }
            }
            
            // 更新数据库中的积分（同步 Redis 的值）
            AuthorInfo author = authorInfoMapper.selectById(authorId);
            if (author != null) {
                int oldFree = author.getFreePoints() != null ? author.getFreePoints() : 0;
                int oldPaid = author.getPaidPoints() != null ? author.getPaidPoints() : 0;
                
                author.setFreePoints(redisFree);
                author.setPaidPoints(redisPaid);
                author.setUpdateTime(LocalDateTime.now());
                
                // 如果使用了免费积分，更新免费积分更新时间
                if (dto.getUsedFreePoints() > 0) {
                    author.setFreePointsUpdateTime(LocalDateTime.now());
                }
                
                authorInfoMapper.updateById(author);
                log.debug("作者[{}]积分已从 Redis 同步到数据库，免费: {} -> {}, 付费: {} -> {}", 
                    authorId, oldFree, redisFree, oldPaid, redisPaid);
            } else {
                log.warn("作者[{}]不存在，跳过积分更新", authorId);
            }
            
            // 2. 记录消费日志
            if (dto.getUsedFreePoints() > 0) {
                try {
                    AuthorPointsConsumeLog freeLog = new AuthorPointsConsumeLog();
                    freeLog.setAuthorId(authorId);
                    freeLog.setConsumeType(dto.getConsumeType());
                    freeLog.setConsumePoints(dto.getUsedFreePoints());
                    freeLog.setPointsType(0); // 免费积分
                    freeLog.setRelatedId(dto.getRelatedId());
                    freeLog.setRelatedDesc(dto.getRelatedDesc());
                    freeLog.setConsumeDate(dto.getConsumeDate());
                    freeLog.setCreateTime(LocalDateTime.now());
                    freeLog.setUpdateTime(LocalDateTime.now());
                    // 加上后缀区分不同类型的积分扣减，同时保证幂等性
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
                    paidLog.setPointsType(1); // 付费积分
                    paidLog.setRelatedId(dto.getRelatedId());
                    paidLog.setRelatedDesc(dto.getRelatedDesc());
                    paidLog.setConsumeDate(dto.getConsumeDate());
                    paidLog.setCreateTime(LocalDateTime.now());
                    paidLog.setUpdateTime(LocalDateTime.now());
                    // 加上后缀区分不同类型的积分扣减，同时保证幂等性
                    paidLog.setIdempotentKey(dto.getIdempotentKey() + "_PAID");
                    logMapper.insert(paidLog);
                } catch (org.springframework.dao.DuplicateKeyException e) {
                     log.warn("消费日志已存在（幂等性拦截），作者ID: {}, Key: {}_PAID", authorId, dto.getIdempotentKey());
                }
            }
            
            log.info("作者[{}]积分消费记录已持久化，免费: {}, 付费: {}", 
                authorId, dto.getUsedFreePoints(), dto.getUsedPaidPoints());
                
        } catch (Exception e) {
            log.error("持久化作者积分消费记录失败: {}", dto, e);
            // 抛出异常，触发 RocketMQ 重试
            throw e;
        }
    }
}

