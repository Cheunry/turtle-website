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

    private final AuthorInfoMapper authorInfoMapper;
    private final AuthorPointsConsumeLogMapper logMapper;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onMessage(AuthorPointsConsumeMqDto dto) {
        log.info("收到作者积分回滚消息: {}", dto);
        
        try {
            Long authorId = dto.getAuthorId();
            
            // 1. 从 Redis 读取最新积分值，同步到数据库
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
                
                authorInfoMapper.updateById(author);
                log.debug("作者[{}]积分回滚后已从 Redis 同步到数据库，免费: {} -> {}, 付费: {} -> {}", 
                    authorId, oldFree, redisFree, oldPaid, redisPaid);
            } else {
                log.warn("作者[{}]不存在，跳过积分回滚更新", authorId);
            }
            
            // 2. 记录回滚日志（标记为回滚类型）
            try {
                AuthorPointsConsumeLog rollbackLog = new AuthorPointsConsumeLog();
                rollbackLog.setAuthorId(authorId);
                rollbackLog.setConsumeType(dto.getConsumeType());
                rollbackLog.setConsumePoints(dto.getConsumePoints());
                rollbackLog.setPointsType(0); // 回滚到免费积分
                rollbackLog.setRelatedId(dto.getRelatedId());
                rollbackLog.setRelatedDesc(dto.getRelatedDesc());
                rollbackLog.setConsumeDate(dto.getConsumeDate());
                rollbackLog.setCreateTime(LocalDateTime.now());
                rollbackLog.setUpdateTime(LocalDateTime.now());
                // 设置幂等性Key
                rollbackLog.setIdempotentKey(dto.getIdempotentKey());
                logMapper.insert(rollbackLog);
            } catch (org.springframework.dao.DuplicateKeyException e) {
                log.warn("回滚日志已存在（幂等性拦截），作者ID: {}, Key: {}", authorId, dto.getIdempotentKey());
            }
            
            log.info("作者[{}]积分回滚记录已持久化，回滚点数: {}", 
                authorId, dto.getConsumePoints());
                
        } catch (Exception e) {
            log.error("持久化作者积分回滚记录失败: {}", dto, e);
            // 抛出异常，触发 RocketMQ 重试
            throw e;
        }
    }
}

