package com.novel.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.novel.user.dao.entity.AuthorInfo;
import com.novel.user.dao.mapper.AuthorInfoMapper;
import com.novel.user.dto.AuthorInfoDto;
import com.novel.user.dto.mq.AuthorPointsConsumeMqDto;
import com.novel.user.dto.req.AuthorPointsConsumeReqDto;
import com.novel.user.dto.req.AuthorRegisterReqDto;
import com.novel.user.service.AuthorInfoService;
import com.novel.common.constant.AmqpConsts;
import com.novel.common.constant.CacheConsts;
import com.novel.common.constant.DatabaseConsts;
import com.novel.common.constant.ErrorCodeEnum;
import com.novel.common.resp.RestResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthorInfoServiceImpl implements AuthorInfoService {

    private final AuthorInfoMapper authorInfoMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final RocketMQTemplate rocketMQTemplate;

    /**
     * 作家注册
     * @param dto 作家注册请求DTO
     * @return Void
     */
    @Override
    public RestResp<Void> authorRegister(AuthorRegisterReqDto dto) {
        // 校验该用户是否已注册为作家
        AuthorInfoDto author = getAuthorInfoByUserId(dto.getUserId());

        if (Objects.nonNull(author)) {
            // 该用户已经是作家，直接返回
            return RestResp.ok();
        }

        // 保存作家注册信息
        AuthorInfo authorInfo = new AuthorInfo();
        authorInfo.setUserId(dto.getUserId());
        authorInfo.setChatAccount(dto.getChatAccount());
        authorInfo.setEmail(dto.getEmail());
        authorInfo.setTelPhone(dto.getTelPhone());
        authorInfo.setPenName(dto.getPenName());
        authorInfo.setWorkDirection(dto.getWorkDirection());
        // 初始化积分字段（数据库有默认值，但显式设置更安全）
        authorInfo.setFreePoints(500);
        authorInfo.setPaidPoints(0);
        authorInfo.setFreePointsUpdateTime(LocalDateTime.now());
        
        authorInfo.setCreateTime(LocalDateTime.now());
        authorInfo.setUpdateTime(LocalDateTime.now());
        authorInfoMapper.insert(authorInfo);

        // 初始化 Redis 中的积分
        Long authorId = authorInfo.getId();
        stringRedisTemplate.opsForValue().set(getFreePointsKey(authorId), "500");
        stringRedisTemplate.opsForValue().set(getPaidPointsKey(authorId), "0");
        log.debug("作者[{}]注册成功，Redis 积分已初始化", authorId);

        return RestResp.ok();
    }


    /**
     * 查询作家状态
     * @param userId 用户ID
     * @return 作家状态
     */
    @Override
    public RestResp<AuthorInfoDto> getStatus(Long userId) {
        AuthorInfoDto authorInfoDto = getAuthorInfoByUserId(userId);

        if (Objects.isNull(authorInfoDto)) {
            return RestResp.ok(null);
        }
        
        // 从 Redis 读取最新积分（如果 Redis 中没有，会从数据库加载）
        Long authorId = authorInfoDto.getId();
        initPointsIfNeeded(authorId);
        
        
        int freePoints = getFreePoints(authorId);
        int paidPoints = getPaidPoints(authorId);
        
        // 更新 DTO 中的积分值
        authorInfoDto.setFreePoints(freePoints);
        authorInfoDto.setPaidPoints(paidPoints);
        
        return RestResp.ok(authorInfoDto);
    }
    
    /**
     * 扣除作者积分（使用 Redis + RocketMQ 方案）
     * @param dto 扣分请求DTO
     * @return Void
     */
    @Override
    public RestResp<Void> deductPoints(AuthorPointsConsumeReqDto dto) {
        Long authorId = dto.getAuthorId();
        
        // 1. 如果 authorId 为空，尝试通过 userId 获取
        if (authorId == null) {
            if (dto.getUserId() == null) {
                return RestResp.fail(ErrorCodeEnum.USER_REQUEST_PARAM_ERROR);
            }
            AuthorInfoDto authorInfoDto = getAuthorInfoByUserId(dto.getUserId());
            if (authorInfoDto == null) {
                return RestResp.fail(ErrorCodeEnum.USER_UN_AUTH);
            }
            authorId = authorInfoDto.getId();
            // 回填 authorId 以便后续日志记录
            dto.setAuthorId(authorId);
        }

        // 2. 幂等性检查：生成唯一标识，防止重复扣分
        String idempotentKey = generateIdempotentKey(authorId, dto.getConsumeType(), 
            dto.getRelatedId(), System.currentTimeMillis());
        String idempotentRedisKey = String.format(CacheConsts.AUTHOR_POINTS_DEDUCT_IDEMPOTENT_KEY,
            authorId, dto.getConsumeType(), 
            dto.getRelatedId() != null ? dto.getRelatedId() : "null",
            idempotentKey);
        
        // 使用 SETNX 实现幂等性控制（24小时过期）
        Boolean isSet = stringRedisTemplate.opsForValue()
            .setIfAbsent(idempotentRedisKey, "1", Duration.ofHours(24));
        
        if (Boolean.FALSE.equals(isSet)) {
            log.warn("作者[{}]积分扣除请求重复，已忽略。消费类型: {}, 关联ID: {}", 
                authorId, dto.getConsumeType(), dto.getRelatedId());
            return RestResp.fail(ErrorCodeEnum.USER_REQUEST_PARAM_ERROR, "重复请求，请勿重复提交");
        }

        // 3. 初始化 Redis 中的积分（如果不存在，从数据库加载）
        initPointsIfNeeded(authorId);

        // 3. 检查并重置免费积分（每日重置）
        LocalDate today = LocalDate.now();
        resetFreePointsIfNeeded(authorId, today);

        // 4. 从 Redis 获取当前积分值
        int currentFreePoints = getFreePoints(authorId);
        int currentPaidPoints = getPaidPoints(authorId);
        
        int consumePoints = dto.getConsumePoints();
        int usedFreePoints = 0;
        int usedPaidPoints = 0;

        // 5. Redis 原子操作：扣除积分
        if (currentFreePoints >= consumePoints) {
            // 免费积分足够，全部使用免费积分
            Long newFree = stringRedisTemplate.opsForValue()
                .decrement(getFreePointsKey(authorId), consumePoints);
            if (newFree == null || newFree < 0) {
                // 回滚
                if (newFree != null && newFree < 0) {
                    stringRedisTemplate.opsForValue()
                        .increment(getFreePointsKey(authorId), consumePoints);
                }
                log.warn("作者[{}]免费积分不足，当前: {}, 需要: {}", authorId, currentFreePoints, consumePoints);
                return RestResp.fail(ErrorCodeEnum.USER_POINTS_NOT_ENOUGH);
            }
            usedFreePoints = consumePoints;
        } else {
            // 混合使用：先用免费，再用付费
            int needPaid = consumePoints - currentFreePoints;
            
            // 先扣除免费积分（如果有）
            if (currentFreePoints > 0) {
                Long newFree = stringRedisTemplate.opsForValue()
                    .decrement(getFreePointsKey(authorId), currentFreePoints);
                if (newFree == null || newFree < 0) {
                    log.warn("作者[{}]扣除免费积分失败", authorId);
                    return RestResp.fail(ErrorCodeEnum.USER_POINTS_NOT_ENOUGH);
                }
                usedFreePoints = currentFreePoints;
            }
            
            // 再扣除付费积分（原子操作）
            Long newPaid = stringRedisTemplate.opsForValue()
                .decrement(getPaidPointsKey(authorId), needPaid);
            if (newPaid == null || newPaid < 0) {
                // 回滚免费积分
                if (usedFreePoints > 0) {
                    stringRedisTemplate.opsForValue()
                        .increment(getFreePointsKey(authorId), usedFreePoints);
                }
                log.warn("作者[{}]付费积分不足，当前: {}, 需要: {}", authorId, currentPaidPoints, needPaid);
                return RestResp.fail(ErrorCodeEnum.USER_POINTS_NOT_ENOUGH);
            }
            usedPaidPoints = needPaid;
        }

        // 6. 发送 MQ 消息，异步持久化到数据库
        try {
            AuthorPointsConsumeMqDto mqDto = AuthorPointsConsumeMqDto.builder()
                .authorId(authorId)
                .consumeType(dto.getConsumeType())
                .consumePoints(consumePoints)
                .usedFreePoints(usedFreePoints)
                .usedPaidPoints(usedPaidPoints)
                .relatedId(dto.getRelatedId())
                .relatedDesc(dto.getRelatedDesc())
                .consumeDate(today)
                .idempotentKey(idempotentKey)
                .build();
                
            String destination = AmqpConsts.AuthorPointsConsumeMq.TOPIC + ":" 
                + AmqpConsts.AuthorPointsConsumeMq.TAG_DEDUCT;
            rocketMQTemplate.convertAndSend(destination, mqDto);
            log.debug("作者[{}]积分消费消息已发送到MQ，消费点数: {}, 幂等性key: {}", 
                authorId, consumePoints, idempotentKey);
        } catch (Exception e) {
            log.error("发送积分消费MQ消息失败，作者ID: {}, 消费点数: {}", authorId, consumePoints, e);
            // MQ 发送失败不影响积分扣除，因为 Redis 已经扣除了
        }

        return RestResp.ok();
    }

    /**
     * 回滚作者积分（补偿机制）
     * 当 AI 服务调用失败时，将已扣除的积分加回去
     * @param dto 扣分请求DTO（包含需要回滚的积分信息）
     * @return Void
     */
    @Override
    public RestResp<Void> rollbackPoints(AuthorPointsConsumeReqDto dto) {
        Long authorId = dto.getAuthorId();
        
        if (authorId == null) {
            if (dto.getUserId() == null) {
                return RestResp.fail(ErrorCodeEnum.USER_REQUEST_PARAM_ERROR);
            }
            AuthorInfoDto authorInfoDto = getAuthorInfoByUserId(dto.getUserId());
            if (authorInfoDto == null) {
                return RestResp.fail(ErrorCodeEnum.USER_UN_AUTH);
            }
            authorId = authorInfoDto.getId();
            dto.setAuthorId(authorId);
        }

        int consumePoints = dto.getConsumePoints();
        if (consumePoints <= 0) {
            log.warn("作者[{}]回滚积分点数无效: {}", authorId, consumePoints);
            return RestResp.fail(ErrorCodeEnum.USER_REQUEST_PARAM_ERROR);
        }

        // 1. 从 Redis 获取当前积分值，计算需要回滚的积分
        initPointsIfNeeded(authorId);
        
        // 2. 根据消费类型和点数，回滚积分（优先回滚到付费积分，再回滚到免费积分）
        // 这里简化处理：全部回滚到免费积分（因为通常免费积分先扣除）
        // 实际可以根据业务需求调整回滚策略
        try {
            Long newFree = stringRedisTemplate.opsForValue()
                .increment(getFreePointsKey(authorId), consumePoints);
            
            if (newFree == null) {
                log.error("作者[{}]回滚积分失败，Redis操作返回null", authorId);
                return RestResp.fail(ErrorCodeEnum.SYSTEM_ERROR);
            }
            
            log.info("作者[{}]积分回滚成功，回滚点数: {}, 当前免费积分: {}", 
                authorId, consumePoints, newFree);
        } catch (Exception e) {
            log.error("作者[{}]回滚积分失败，回滚点数: {}", authorId, consumePoints, e);
            return RestResp.fail(ErrorCodeEnum.SYSTEM_ERROR);
        }

        // 3. 发送 MQ 消息，异步持久化回滚记录到数据库
        LocalDate today = LocalDate.now();
        try {
            AuthorPointsConsumeMqDto mqDto = AuthorPointsConsumeMqDto.builder()
                .authorId(authorId)
                .consumeType(dto.getConsumeType())
                .consumePoints(consumePoints)
                .usedFreePoints(consumePoints) // 回滚时，全部回滚到免费积分
                .usedPaidPoints(0)
                .relatedId(dto.getRelatedId())
                .relatedDesc(dto.getRelatedDesc() != null ? 
                    "回滚: " + dto.getRelatedDesc() : "积分回滚")
                .consumeDate(today)
                .idempotentKey(generateIdempotentKey(authorId, dto.getConsumeType(), 
                    dto.getRelatedId(), System.currentTimeMillis()))
                .build();
                
            String destination = AmqpConsts.AuthorPointsConsumeMq.TOPIC + ":" 
                + AmqpConsts.AuthorPointsConsumeMq.TAG_ROLLBACK;
            rocketMQTemplate.convertAndSend(destination, mqDto);
            log.debug("作者[{}]积分回滚消息已发送到MQ，回滚点数: {}", authorId, consumePoints);
        } catch (Exception e) {
            log.error("发送积分回滚MQ消息失败，作者ID: {}, 回滚点数: {}", authorId, consumePoints, e);
            // MQ 发送失败不影响积分回滚，因为 Redis 已经回滚了
        }

        return RestResp.ok();
    }

    /**
     * 生成幂等性唯一标识
     */
    private String generateIdempotentKey(Long authorId, Integer consumeType, 
                                         Long relatedId, long timestamp) {
        return String.format("%s_%s_%s_%s_%s", 
            authorId, 
            consumeType, 
            relatedId != null ? relatedId : "null",
            timestamp,
            UUID.randomUUID().toString().substring(0, 8));
    }

    /**
     * 初始化积分（如果 Redis 中不存在，从数据库加载）
     */
    private void initPointsIfNeeded(Long authorId) {
        String freeKey = getFreePointsKey(authorId);
        String paidKey = getPaidPointsKey(authorId);
        
        boolean freeExists = stringRedisTemplate.hasKey(freeKey);
        boolean paidExists = stringRedisTemplate.hasKey(paidKey);
        
        if (!freeExists || !paidExists) {
            // 从数据库加载
            AuthorInfo author = authorInfoMapper.selectById(authorId);
            if (author != null) {
                int free = author.getFreePoints() != null ? author.getFreePoints() : 0;
                int paid = author.getPaidPoints() != null ? author.getPaidPoints() : 0;
                
                if (!freeExists) {
                    stringRedisTemplate.opsForValue().set(freeKey, String.valueOf(free));
                }
                if (!paidExists) {
                    stringRedisTemplate.opsForValue().set(paidKey, String.valueOf(paid));
                }
                log.debug("作者[{}]积分已从数据库加载到Redis，免费: {}, 付费: {}", authorId, free, paid);
            }
        }
    }

    /**
     * 每日重置免费积分
     */
    private void resetFreePointsIfNeeded(Long authorId, LocalDate today) {
        String resetKey = String.format(CacheConsts.AUTHOR_FREE_POINTS_RESET_KEY, authorId, today);
        String freeKey = getFreePointsKey(authorId);
        
        // 使用 SETNX 实现每日只重置一次
        Boolean isSet = stringRedisTemplate.opsForValue()
            .setIfAbsent(resetKey, "1", Duration.ofDays(1));
            
        if (Boolean.TRUE.equals(isSet)) {
            // 今天第一次使用，重置免费积分为 500
            stringRedisTemplate.opsForValue().set(freeKey, "500");
            log.debug("作者[{}]免费积分已重置为500", authorId);
        }
    }

    /**
     * 获取免费积分
     */
    private int getFreePoints(Long authorId) {
        String value = stringRedisTemplate.opsForValue().get(getFreePointsKey(authorId));
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            log.error("解析免费积分失败，作者ID: {}, 值: {}", authorId, value, e);
            return 0;
        }
    }

    /**
     * 获取付费积分
     */
    private int getPaidPoints(Long authorId) {
        String value = stringRedisTemplate.opsForValue().get(getPaidPointsKey(authorId));
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            log.error("解析付费积分失败，作者ID: {}, 值: {}", authorId, value, e);
            return 0;
        }
    }

    /**
     * 获取免费积分 Redis Key
     */
    private String getFreePointsKey(Long authorId) {
        return String.format(CacheConsts.AUTHOR_FREE_POINTS_KEY, authorId);
    }

    /**
     * 获取付费积分 Redis Key
     */
    private String getPaidPointsKey(Long authorId) {
        return String.format(CacheConsts.AUTHOR_PAID_POINTS_KEY, authorId);
    }


    /**
     * 查询作家信息
     * @param userId 用户ID
     * @return 作家基础信息DTO
     */
    public AuthorInfoDto getAuthorInfoByUserId(Long userId) {
        QueryWrapper<AuthorInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper
                .eq(DatabaseConsts.AuthorInfoTable.COLUMN_USER_ID, userId)
                .last(DatabaseConsts.SqlEnum.LIMIT_1.getSql());
        AuthorInfo authorInfo = authorInfoMapper.selectOne(queryWrapper);
        if (Objects.isNull(authorInfo)) {
            return null;
        }
        return AuthorInfoDto.builder()
                .id(authorInfo.getId())
                .penName(authorInfo.getPenName())
                .status(authorInfo.getStatus())
                .freePoints(authorInfo.getFreePoints())
                .paidPoints(authorInfo.getPaidPoints())
                .build();
    }

}
