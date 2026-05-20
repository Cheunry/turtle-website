package com.novel.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.novel.user.dao.entity.AuthorInfo;
import com.novel.user.dao.entity.AuthorPointsConsumeLog;
import com.novel.user.dao.entity.AuthorPointsTx;
import com.novel.user.dao.mapper.AuthorInfoMapper;
import com.novel.user.dao.mapper.AuthorPointsConsumeLogMapper;
import com.novel.user.dao.mapper.AuthorPointsTxMapper;
import com.novel.user.dto.AuthorInfoDto;
import com.novel.user.dto.mq.AuthorPointsConsumeMqDto;
import com.novel.user.dto.req.AuthorPointsConsumeReqDto;
import com.novel.user.dto.req.AuthorRegisterReqDto;
import com.novel.user.dto.req.CoverGenerationFailedReqDto;
import com.novel.user.service.AuthorService;
import com.novel.user.service.CacheService;
import com.novel.user.service.MessageService;
import com.novel.user.feign.BookFeignManager;
import com.novel.user.dto.req.MessagePageReqDto;
import com.novel.user.dto.resp.MessageRespDto;
import com.novel.book.dto.req.*;
import com.novel.book.dto.resp.BookChapterRespDto;
import com.novel.book.dto.resp.BookInfoRespDto;
import com.novel.book.dto.mq.BookSubmitMqDto;
import com.novel.book.dto.mq.ChapterSubmitMqDto;
import com.novel.ai.dto.req.CoverImageAsyncSubmitReqDto;
import com.novel.ai.dto.resp.ImageGenJobStatusRespDto;
import com.novel.ai.dto.resp.ImageGenJobSubmitRespDto;
import com.novel.ai.feign.AiFeign;
import com.novel.book.dto.req.BookAuditReqDto;
import com.novel.book.dto.req.ChapterAuditReqDto;
import com.novel.book.dto.req.BookCoverReqDto;
import com.novel.ai.dto.req.TextPolishReqDto;
import com.novel.book.dto.resp.BookAuditRespDto;
import com.novel.book.dto.resp.ChapterAuditRespDto;
import com.novel.ai.dto.resp.TextPolishRespDto;
import com.novel.common.constant.AmqpConsts;
import com.novel.common.constant.ApiRouterConsts;
import com.novel.common.constant.CacheConsts;
import com.novel.common.constant.DatabaseConsts;
import com.novel.common.constant.ErrorCodeEnum;
import com.novel.common.req.PageReqDto;
import com.novel.common.resp.PageRespDto;
import com.novel.common.resp.RestResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthorServiceImpl implements AuthorService {

    private final AuthorInfoMapper authorInfoMapper;
    private final AuthorPointsConsumeLogMapper authorPointsConsumeLogMapper;
    private final AuthorPointsTxMapper authorPointsTxMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final RocketMQTemplate rocketMQTemplate;
    private final TransactionTemplate transactionTemplate;
    private final BookFeignManager bookFeignManager;
    private final MessageService messageService;
    private final AiFeign aiFeign;
    private final WebClient aiInnerWebClient;
    private final CacheService cacheService;

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

        // 清除作者信息缓存（新注册时，确保下次查询获取最新数据）
        cacheService.evictAuthorInfoCacheByUserId(dto.getUserId());
        cacheService.evictAuthorInfoCacheByAuthorId(authorId);

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
        
        Long authorId = authorInfoDto.getId();
        PointsBalance pointsBalance = loadAuthorPointsFromDb(authorId, LocalDate.now());
        syncRedisPoints(authorId, pointsBalance.freePoints(), pointsBalance.paidPoints());
        
        // 更新 DTO 中的积分值
        authorInfoDto.setFreePoints(pointsBalance.freePoints());
        authorInfoDto.setPaidPoints(pointsBalance.paidPoints());
        
        return RestResp.ok(authorInfoDto);
    }
    
    /**
     * 扣除作者积分（MySQL 主账本事务扣减，Redis 仅作缓存）
     *
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

        String idempotentKey = resolveDeductIdempotentKey(authorId, dto);
        LocalDate today = LocalDate.now();
        PointsDeductResult deductResult;
        try {
            Long deductAuthorId = authorId;
            deductResult = transactionTemplate.execute(status -> deductPointsInDb(deductAuthorId, dto, today, idempotentKey));
        } catch (PointsNotEnoughException e) {
            log.warn("作者[{}]积分不足，消费点数: {}, requestId: {}", authorId, dto.getConsumePoints(), idempotentKey);
            return RestResp.fail(ErrorCodeEnum.USER_POINTS_NOT_ENOUGH);
        } catch (Exception e) {
            log.error("作者[{}]积分扣除失败，requestId: {}", authorId, idempotentKey, e);
            return RestResp.fail(ErrorCodeEnum.SYSTEM_ERROR, "积分扣除失败，请稍后重试");
        }

        if (deductResult == null) {
            return RestResp.fail(ErrorCodeEnum.SYSTEM_ERROR);
        }

        if (deductResult.duplicate()) {
            dto.setUsedFreePoints(0);
            dto.setUsedPaidPoints(0);
            dto.setDeductSkipped(true);
            log.info("作者[{}]积分扣除请求重复，按幂等成功返回。消费类型: {}, 关联ID: {}, requestId: {}",
                authorId, dto.getConsumeType(), dto.getRelatedId(), idempotentKey);
            return RestResp.ok();
        }

        // 保存使用的积分信息到dto，用于后续回滚
        dto.setUsedFreePoints(deductResult.usedFreePoints());
        dto.setUsedPaidPoints(deductResult.usedPaidPoints());
        dto.setDeductSkipped(false);
        syncRedisPoints(authorId, deductResult.freePoints(), deductResult.paidPoints());

        // 6. 发送 MQ 消息，异步持久化到数据库
        try {
            AuthorPointsConsumeMqDto mqDto = AuthorPointsConsumeMqDto.builder()
                .authorId(authorId)
                .consumeType(dto.getConsumeType())
                .consumePoints(dto.getConsumePoints())
                .usedFreePoints(deductResult.usedFreePoints())
                .usedPaidPoints(deductResult.usedPaidPoints())
                .relatedId(dto.getRelatedId())
                .relatedDesc(dto.getRelatedDesc())
                .consumeDate(today)
                .idempotentKey(idempotentKey)
                .build();
                
            String destination = AmqpConsts.AuthorPointsConsumeMq.TOPIC + ":" 
                + AmqpConsts.AuthorPointsConsumeMq.TAG_DEDUCT;
            rocketMQTemplate.convertAndSend(destination, mqDto);
            log.debug("作者[{}]积分消费消息已发送到MQ，消费点数: {}, 幂等性key: {}", 
                authorId, dto.getConsumePoints(), idempotentKey);
        } catch (Exception e) {
            log.error("发送积分消费MQ消息失败，作者ID: {}, 消费点数: {}", authorId, dto.getConsumePoints(), e);
            // MQ 发送失败不影响积分扣除，MySQL 主账本已完成事务扣减
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

        if (Boolean.TRUE.equals(dto.getDeductSkipped())) {
            log.info("作者[{}]本次积分扣减被幂等拦截，无需回滚。requestId: {}", authorId, dto.getRequestId());
            return RestResp.ok();
        }

        String requestId = dto.getRequestId();
        if (requestId == null || requestId.isBlank()) {
            log.warn("作者[{}]回滚积分缺少原扣减 requestId", authorId);
            return RestResp.fail(ErrorCodeEnum.USER_REQUEST_PARAM_ERROR);
        }
        requestId = requestId.trim();

        String rollbackKey = requestId + ":ROLLBACK";
        if (selectPointsTx(rollbackKey) != null) {
            log.info("作者[{}]积分已回滚过，按幂等成功返回。requestId: {}", authorId, requestId);
            return RestResp.ok();
        }

        AuthorPointsTx deductTx = authorPointsTxMapper.selectOne(
            new QueryWrapper<AuthorPointsTx>()
                .eq("request_id", requestId)
                .eq("author_id", authorId)
                .eq("tx_type", 1));
        if (deductTx == null) {
            log.warn("作者[{}]回滚积分未找到原扣减事务。requestId: {}", authorId, requestId);
            return RestResp.fail(ErrorCodeEnum.USER_REQUEST_PARAM_ERROR, "未找到原扣减事务，无法回滚");
        }
        if (Objects.equals(deductTx.getStatus(), 2)) {
            log.info("作者[{}]原扣减事务已标记回滚，按幂等成功返回。requestId: {}", authorId, requestId);
            return RestResp.ok();
        }

        int usedFreePoints = Math.abs(Math.min(deductTx.getFreePointsDelta() != null ? deductTx.getFreePointsDelta() : 0, 0));
        int usedPaidPoints = Math.abs(Math.min(deductTx.getPaidPointsDelta() != null ? deductTx.getPaidPointsDelta() : 0, 0));
        int rollbackPoints = usedFreePoints + usedPaidPoints;
        if (rollbackPoints <= 0) {
            log.warn("作者[{}]原扣减事务积分无效。requestId: {}, free: {}, paid: {}",
                authorId, requestId, usedFreePoints, usedPaidPoints);
            return RestResp.fail(ErrorCodeEnum.USER_REQUEST_PARAM_ERROR);
        }
        final Long rollbackAuthorId = authorId;

        try {
            PointsBalance balance = transactionTemplate.execute(status -> {
                AuthorInfo author = authorInfoMapper.selectOne(
                    new QueryWrapper<AuthorInfo>()
                        .eq("id", rollbackAuthorId)
                        .last("for update"));
                if (author == null) {
                    throw new IllegalStateException("作者不存在");
                }

                if (selectPointsTx(rollbackKey) != null) {
                    return null;
                }

                int newFreePoints = (author.getFreePoints() != null ? author.getFreePoints() : 0) + usedFreePoints;
                int newPaidPoints = (author.getPaidPoints() != null ? author.getPaidPoints() : 0) + usedPaidPoints;
                LocalDateTime now = LocalDateTime.now();
                author.setFreePoints(newFreePoints);
                author.setPaidPoints(newPaidPoints);
                author.setUpdateTime(now);
                if (usedFreePoints > 0) {
                    author.setFreePointsUpdateTime(now);
                }
                authorInfoMapper.updateById(author);

                AuthorPointsTx rollbackTx = new AuthorPointsTx();
                rollbackTx.setRequestId(rollbackKey);
                rollbackTx.setAuthorId(rollbackAuthorId);
                rollbackTx.setTxType(2);
                rollbackTx.setConsumeType(deductTx.getConsumeType());
                rollbackTx.setFreePointsDelta(usedFreePoints);
                rollbackTx.setPaidPointsDelta(usedPaidPoints);
                rollbackTx.setStatus(1);
                rollbackTx.setRelatedId(deductTx.getRelatedId());
                rollbackTx.setRelatedDesc(deductTx.getRelatedDesc());
                rollbackTx.setCreateTime(now);
                rollbackTx.setUpdateTime(now);
                authorPointsTxMapper.insert(rollbackTx);

                deductTx.setStatus(2);
                deductTx.setUpdateTime(now);
                authorPointsTxMapper.updateById(deductTx);

                AuthorPointsConsumeLog rollbackLog = new AuthorPointsConsumeLog();
                rollbackLog.setAuthorId(rollbackAuthorId);
                rollbackLog.setConsumeType(deductTx.getConsumeType());
                rollbackLog.setConsumePoints(rollbackPoints);
                rollbackLog.setPointsType(0);
                rollbackLog.setRelatedId(deductTx.getRelatedId());
                rollbackLog.setRelatedDesc(deductTx.getRelatedDesc() != null ? "回滚: " + deductTx.getRelatedDesc() : "积分回滚");
                rollbackLog.setConsumeDate(LocalDate.now());
                rollbackLog.setCreateTime(now);
                rollbackLog.setUpdateTime(now);
                rollbackLog.setIdempotentKey(rollbackKey);
                authorPointsConsumeLogMapper.insert(rollbackLog);
                return new PointsBalance(newFreePoints, newPaidPoints);
            });

            if (balance == null) {
                log.info("作者[{}]积分已回滚过，按幂等成功返回。requestId: {}", authorId, requestId);
                return RestResp.ok();
            }

            syncRedisPoints(authorId, balance.freePoints(), balance.paidPoints());
            log.info("作者[{}]积分回滚成功，requestId: {}, 回滚免费积分: {}, 回滚付费积分: {}",
                authorId, requestId, usedFreePoints, usedPaidPoints);
        } catch (Exception e) {
            log.error("作者[{}]积分回滚失败，requestId: {}, 回滚免费积分: {}, 回滚付费积分: {}",
                authorId, requestId, usedFreePoints, usedPaidPoints, e);
            return RestResp.fail(ErrorCodeEnum.SYSTEM_ERROR);
        }

        return RestResp.ok();
    }

    @Override
    public RestResp<Void> handleCoverGenerationFailed(CoverGenerationFailedReqDto dto) {
        if (dto == null || dto.getAuthorId() == null || dto.getRequestId() == null || dto.getRequestId().isBlank()) {
            return RestResp.fail(ErrorCodeEnum.USER_REQUEST_PARAM_ERROR);
        }

        String requestId = dto.getRequestId().trim();
        String reason = dto.getFailureReason();
        if (reason != null && reason.length() > 300) {
            reason = reason.substring(0, 300);
        }

        log.warn("收到AI封面生图失败回调，准备统一回滚积分。authorId={}, jobId={}, requestId={}, reason={}",
                dto.getAuthorId(), dto.getJobId(), requestId, reason);

        AuthorPointsConsumeReqDto rollbackReq = AuthorPointsConsumeReqDto.builder()
                .authorId(dto.getAuthorId())
                .requestId(requestId)
                .consumeType(2)
                .consumePoints(0)
                .build();
        RestResp<Void> rollbackResp = rollbackPoints(rollbackReq);
        if (!rollbackResp.isOk()) {
            log.error("AI封面生图失败回调触发积分回滚失败，authorId={}, jobId={}, requestId={}, msg={}",
                    dto.getAuthorId(), dto.getJobId(), requestId, rollbackResp.getMessage());
        }
        return rollbackResp;
    }

    /**
     * 解析扣减幂等号。优先使用调用方传入的稳定 requestId，避免重复提交时生成新 key。
     */
    private String resolveDeductIdempotentKey(Long authorId, AuthorPointsConsumeReqDto dto) {
        String requestId = dto.getRequestId();
        if (requestId != null && !requestId.isBlank()) {
            return requestId.trim();
        }
        String fallbackKey = String.format("LEGACY:%s:%s:%s:%s",
            authorId,
            dto.getConsumeType(),
            dto.getRelatedId() != null ? dto.getRelatedId() : "null",
            System.currentTimeMillis());
        log.warn("作者[{}]积分扣除请求未传 requestId，使用临时幂等号: {}", authorId, fallbackKey);
        return fallbackKey;
    }

    private PointsDeductResult deductPointsInDb(Long authorId,
                                                AuthorPointsConsumeReqDto dto,
                                                LocalDate today,
                                                String requestId) {
        AuthorPointsTx existingTx = selectPointsTx(requestId);
        if (existingTx != null) {
            return PointsDeductResult.duplicateResult();
        }

        AuthorInfo author = authorInfoMapper.selectOne(
            new QueryWrapper<AuthorInfo>()
                .eq("id", authorId)
                .last("for update"));
        if (author == null) {
            throw new IllegalStateException("作者不存在");
        }

        existingTx = selectPointsTx(requestId);
        if (existingTx != null) {
            return PointsDeductResult.duplicateResult();
        }

        LocalDateTime now = LocalDateTime.now();
        resetAuthorFreePointsIfNeeded(author, today, now);

        int currentFreePoints = author.getFreePoints() != null ? author.getFreePoints() : 0;
        int currentPaidPoints = author.getPaidPoints() != null ? author.getPaidPoints() : 0;
        int consumePoints = dto.getConsumePoints() != null ? dto.getConsumePoints() : 0;
        if (consumePoints <= 0) {
            throw new IllegalArgumentException("消费积分必须大于0");
        }

        int usedFreePoints = Math.min(currentFreePoints, consumePoints);
        int usedPaidPoints = consumePoints - usedFreePoints;
        if (currentPaidPoints < usedPaidPoints) {
            throw new PointsNotEnoughException();
        }

        int newFreePoints = currentFreePoints - usedFreePoints;
        int newPaidPoints = currentPaidPoints - usedPaidPoints;
        author.setFreePoints(newFreePoints);
        author.setPaidPoints(newPaidPoints);
        author.setUpdateTime(now);
        if (usedFreePoints > 0) {
            author.setFreePointsUpdateTime(now);
        }
        authorInfoMapper.updateById(author);

        AuthorPointsTx tx = new AuthorPointsTx();
        tx.setRequestId(requestId);
        tx.setAuthorId(authorId);
        tx.setTxType(1);
        tx.setConsumeType(dto.getConsumeType());
        tx.setFreePointsDelta(-usedFreePoints);
        tx.setPaidPointsDelta(-usedPaidPoints);
        tx.setStatus(1);
        tx.setRelatedId(dto.getRelatedId());
        tx.setRelatedDesc(dto.getRelatedDesc());
        tx.setCreateTime(now);
        tx.setUpdateTime(now);
        authorPointsTxMapper.insert(tx);

        insertDeductLogIfNeeded(authorId, dto, usedFreePoints, 0, today, requestId + "_FREE", now);
        insertDeductLogIfNeeded(authorId, dto, usedPaidPoints, 1, today, requestId + "_PAID", now);

        return new PointsDeductResult(false, usedFreePoints, usedPaidPoints, newFreePoints, newPaidPoints);
    }

    private AuthorPointsTx selectPointsTx(String requestId) {
        return authorPointsTxMapper.selectOne(
            new QueryWrapper<AuthorPointsTx>()
                .eq("request_id", requestId));
    }

    private boolean resetAuthorFreePointsIfNeeded(AuthorInfo author, LocalDate today, LocalDateTime now) {
        LocalDateTime updateTime = author.getFreePointsUpdateTime();
        if (updateTime == null || updateTime.toLocalDate().isBefore(today)) {
            author.setFreePoints(500);
            author.setFreePointsUpdateTime(now);
            return true;
        }
        return false;
    }

    private PointsBalance loadAuthorPointsFromDb(Long authorId, LocalDate today) {
        PointsBalance balance = transactionTemplate.execute(status -> {
            AuthorInfo author = authorInfoMapper.selectOne(
                new QueryWrapper<AuthorInfo>()
                    .eq("id", authorId)
                    .last("for update"));
            if (author == null) {
                return new PointsBalance(0, 0);
            }

            LocalDateTime now = LocalDateTime.now();
            if (resetAuthorFreePointsIfNeeded(author, today, now)) {
                author.setUpdateTime(now);
                authorInfoMapper.updateById(author);
            }

            int freePoints = author.getFreePoints() != null ? author.getFreePoints() : 0;
            int paidPoints = author.getPaidPoints() != null ? author.getPaidPoints() : 0;
            return new PointsBalance(freePoints, paidPoints);
        });
        return balance != null ? balance : new PointsBalance(0, 0);
    }

    private void insertDeductLogIfNeeded(Long authorId,
                                         AuthorPointsConsumeReqDto dto,
                                         int consumePoints,
                                         int pointsType,
                                         LocalDate consumeDate,
                                         String idempotentKey,
                                         LocalDateTime now) {
        if (consumePoints <= 0) {
            return;
        }
        try {
            AuthorPointsConsumeLog consumeLog = new AuthorPointsConsumeLog();
            consumeLog.setAuthorId(authorId);
            consumeLog.setConsumeType(dto.getConsumeType());
            consumeLog.setConsumePoints(consumePoints);
            consumeLog.setPointsType(pointsType);
            consumeLog.setRelatedId(dto.getRelatedId());
            consumeLog.setRelatedDesc(dto.getRelatedDesc());
            consumeLog.setConsumeDate(consumeDate);
            consumeLog.setCreateTime(now);
            consumeLog.setUpdateTime(now);
            consumeLog.setIdempotentKey(idempotentKey);
            authorPointsConsumeLogMapper.insert(consumeLog);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            log.info("扣减流水已存在，跳过重复插入。作者ID: {}, Key: {}", authorId, idempotentKey);
        }
    }

    private void syncRedisPoints(Long authorId, int freePoints, int paidPoints) {
        try {
            stringRedisTemplate.opsForValue().set(getFreePointsKey(authorId), String.valueOf(freePoints));
            stringRedisTemplate.opsForValue().set(getPaidPointsKey(authorId), String.valueOf(paidPoints));
        } catch (Exception e) {
            log.warn("同步作者积分到 Redis 失败，作者ID: {}, free: {}, paid: {}",
                authorId, freePoints, paidPoints, e);
        }
    }

    private record PointsBalance(int freePoints, int paidPoints) {
    }

    private record PointsDeductResult(boolean duplicate,
                                      int usedFreePoints,
                                      int usedPaidPoints,
                                      int freePoints,
                                      int paidPoints) {
        private static PointsDeductResult duplicateResult() {
            return new PointsDeductResult(true, 0, 0, 0, 0);
        }
    }

    private static class PointsNotEnoughException extends RuntimeException {
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
    @Override
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

    @Override
    public RestResp<Void> publishBook(Long authorId, String penName, BookAddReqDto dto, Boolean auditEnable) {
        // 构建MQ消息
        BookSubmitMqDto mqDto = BookSubmitMqDto.builder()
                .operationType("ADD")
                .authorId(authorId)
                .penName(penName)
                .workDirection(dto.getWorkDirection())
                .categoryId(dto.getCategoryId())
                .categoryName(dto.getCategoryName())
                .picUrl(dto.getPicUrl())
                .bookName(dto.getBookName())
                .bookDesc(dto.getBookDesc())
                .isVip(dto.getIsVip())
                .bookStatus(dto.getBookStatus())
                .auditEnable(auditEnable)
                .build();
        
        // 发送MQ消息
        try {
            String destination = AmqpConsts.BookSubmitMq.TOPIC + ":" + AmqpConsts.BookSubmitMq.TAG_SUBMIT;
            rocketMQTemplate.convertAndSend(destination, mqDto);
            log.debug("书籍新增请求已发送到MQ，bookName: {}, authorId: {}", dto.getBookName(), authorId);
        } catch (Exception e) {
            log.error("发送书籍新增MQ消息失败，bookName: {}, authorId: {}", dto.getBookName(), authorId, e);
            return RestResp.fail(ErrorCodeEnum.SYSTEM_ERROR, "提交失败，请稍后重试");
        }
        
        return RestResp.ok();
    }

    @Override
    public RestResp<Void> updateBook(Long authorId, Long bookId, BookUptReqDto dto, Boolean auditEnable) {
        // 构建MQ消息
        BookSubmitMqDto mqDto = BookSubmitMqDto.builder()
                .operationType("UPDATE")
                .bookId(bookId)
                .authorId(authorId)
                .picUrl(dto.getPicUrl())
                .bookName(dto.getBookName())
                .bookDesc(dto.getBookDesc())
                .categoryId(dto.getCategoryId())
                .categoryName(dto.getCategoryName())
                .workDirection(dto.getWorkDirection())
                .isVip(dto.getIsVip())
                .bookStatus(dto.getBookStatus())
                .auditEnable(auditEnable)
                .build();
        
        // 发送MQ消息
        try {
            String destination = AmqpConsts.BookSubmitMq.TOPIC + ":" + AmqpConsts.BookSubmitMq.TAG_SUBMIT;
            rocketMQTemplate.convertAndSend(destination, mqDto);
            log.debug("书籍更新请求已发送到MQ，bookId: {}, authorId: {}", bookId, authorId);
        } catch (Exception e) {
            log.error("发送书籍更新MQ消息失败，bookId: {}, authorId: {}", bookId, authorId, e);
            return RestResp.fail(ErrorCodeEnum.SYSTEM_ERROR, "提交失败，请稍后重试");
        }
        
        return RestResp.ok();
    }

    @Override
    public RestResp<Void> deleteBook(Long bookId) {
        BookDelReqDto dto = new BookDelReqDto();
        dto.setBookId(bookId);
        return bookFeignManager.deleteBook(dto);
    }

    @Override
    public RestResp<Void> publishBookChapter(Long authorId, Long bookId, ChapterAddReqDto dto, Boolean auditEnable) {
        dto.setAuthorId(authorId);
        dto.setBookId(bookId);
        
        // 构建章节提交MQ消息
        ChapterSubmitMqDto submitDto = ChapterSubmitMqDto.builder()
                .bookId(bookId)
                .authorId(authorId)
                .chapterNum(dto.getChapterNum())
                .chapterName(dto.getChapterName())
                .content(dto.getContent())
                .isVip(dto.getIsVip())
                .operationType("CREATE")
                .auditEnable(auditEnable)
                .build();
        
        // 发送MQ消息
        try {
            String destination = AmqpConsts.ChapterSubmitMq.TOPIC + ":" + AmqpConsts.ChapterSubmitMq.TAG_SUBMIT;
            rocketMQTemplate.convertAndSend(destination, submitDto);
            log.debug("章节新增请求已发送到MQ，bookId: {}, chapterNum: {}", bookId, dto.getChapterNum());
        } catch (Exception e) {
            log.error("发送章节新增MQ消息失败，bookId: {}, chapterNum: {}", bookId, dto.getChapterNum(), e);
            return RestResp.fail(ErrorCodeEnum.SYSTEM_ERROR, "提交失败，请稍后重试");
        }
        
        return RestResp.ok();
    }

    @Override
    public RestResp<Void> updateBookChapter(Long authorId, Long bookId, Integer chapterNum, ChapterUptReqDto dto, Boolean auditEnable) {
        dto.setBookId(bookId);
        dto.setOldChapterNum(chapterNum);
        dto.setAuthorId(authorId);
        
        // 构建章节提交MQ消息
        ChapterSubmitMqDto submitDto = ChapterSubmitMqDto.builder()
                .bookId(bookId)
                .authorId(authorId)
                .oldChapterNum(chapterNum)
                .chapterNum(dto.getChapterNum())
                .chapterName(dto.getChapterName())
                .content(dto.getContent())
                .isVip(dto.getIsVip())
                .operationType("UPDATE")
                .auditEnable(auditEnable)
                .build();
        
        // 发送MQ消息
        try {
            String destination = AmqpConsts.ChapterSubmitMq.TOPIC + ":" + AmqpConsts.ChapterSubmitMq.TAG_SUBMIT;
            rocketMQTemplate.convertAndSend(destination, submitDto);
            log.debug("章节更新请求已发送到MQ，bookId: {}, chapterNum: {}", bookId, chapterNum);
        } catch (Exception e) {
            log.error("发送章节更新MQ消息失败，bookId: {}, chapterNum: {}", bookId, chapterNum, e);
            return RestResp.fail(ErrorCodeEnum.SYSTEM_ERROR, "提交失败，请稍后重试");
        }
        
        return RestResp.ok();
    }

    @Override
    public RestResp<Void> deleteBookChapter(Long bookId, Integer chapterNum) {
        ChapterDelReqDto dto = new ChapterDelReqDto();
        dto.setBookId(bookId);
        dto.setChapterNum(chapterNum);
        return bookFeignManager.deleteBookChapter(dto);
    }

    @Override
    public RestResp<PageRespDto<BookInfoRespDto>> listBooks(Long authorId, BookPageReqDto dto) {
        dto.setAuthorId(authorId);
        return bookFeignManager.listPublishBooks(dto);
    }

    @Override
    public RestResp<PageRespDto<BookChapterRespDto>> listBookChapters(Long authorId, Long bookId, PageReqDto dto) {
        ChapterPageReqDto chapterPageReqDto = new ChapterPageReqDto();
        chapterPageReqDto.setBookId(bookId);
        chapterPageReqDto.setPageNum(dto.getPageNum());
        chapterPageReqDto.setPageSize(dto.getPageSize());
        chapterPageReqDto.setAuthorId(authorId);
        return bookFeignManager.listPublishBookChapters(chapterPageReqDto);
    }

    @Override
    public RestResp<BookChapterRespDto> getBookChapter(Long bookId, Integer chapterNum) {
        return bookFeignManager.getBookChapter(bookId, chapterNum);
    }

    @Override
    public RestResp<BookInfoRespDto> getBookById(Long bookId) {
        return bookFeignManager.getBookByIdForAuthor(bookId);
    }

    @Override
    public RestResp<PageRespDto<MessageRespDto>> listAuthorMessages(MessagePageReqDto pageReqDto) {
        // 明确指定只查询作者消息（receiver_type=1），避免与普通用户消息混淆
        pageReqDto.setReceiverType(DatabaseConsts.MessageReceiveTable.RECEIVER_TYPE_AUTHOR);
        // 如果未指定消息类型，默认只查作家相关的消息（类型2:作家助手/审核）
        if (pageReqDto.getMessageType() == null) {
            pageReqDto.setMessageType(DatabaseConsts.MessageContentTable.MESSAGE_TYPE_AUTHOR_ASSISTANT);
        }
        return messageService.listMessages(pageReqDto);
    }

    @Override
    public RestResp<Long> getAuthorUnReadCount() {
        // 调用专门的方法统计作者消息（receiver_type=1）
        MessageServiceImpl messageServiceImpl = (MessageServiceImpl) messageService;
        return messageServiceImpl.getUnReadCountByReceiverType(DatabaseConsts.MessageContentTable.MESSAGE_TYPE_AUTHOR_ASSISTANT, DatabaseConsts.MessageReceiveTable.RECEIVER_TYPE_AUTHOR);
    }

    @Override
    public RestResp<Void> readAuthorMessage(Long id) {
        return messageService.readMessage(id);
    }

    @Override
    public RestResp<Void> deleteAuthorMessage(Long id) {
        return messageService.deleteMessage(id);
    }

    @Override
    public RestResp<Void> batchReadAuthorMessages(java.util.List<Long> ids) {
        return messageService.batchReadMessages(2, ids);
    }

    @Override
    public RestResp<Void> batchDeleteAuthorMessages(java.util.List<Long> ids) {
        return messageService.batchDeleteMessages(2, ids);
    }

    @Override
    public RestResp<Void> allReadAuthorMessages() {
        return messageService.allReadMessages(2);
    }

    @Override
    public RestResp<Void> allDeleteAuthorMessages() {
        return messageService.allDeleteMessages(2);
    }

    @Override
    public RestResp<Object> audit(Long authorId, AuthorPointsConsumeReqDto dto) {
        dto.setAuthorId(authorId);
        dto.setConsumeType(0); // 0-AI审核
        dto.setConsumePoints(1); // 1点/次
        
        // 1. 先扣除积分
        RestResp<Void> deductResult = deductPoints(dto);
        if (!deductResult.isOk()) {
            return RestResp.fail(ErrorCodeEnum.USER_POINTS_NOT_ENOUGH, deductResult.getMessage());
        }
        
        // 2. 调用AI审核服务
        try {
            Object result;
            // 判断是审核书籍还是审核章节
            if (dto.getChapterNum() != null || (dto.getContent() != null && !dto.getContent().isEmpty())) {
                // 审核章节
                ChapterAuditReqDto chapterReq = ChapterAuditReqDto.builder()
                        .bookId(dto.getRelatedId())
                        .chapterNum(dto.getChapterNum())
                        .chapterName(dto.getTitle())
                        .content(dto.getContent())
                        .build();
                RestResp<ChapterAuditRespDto> aiResp = aiFeign.auditChapter(chapterReq);
                if (!aiResp.isOk()) {
                     throw new RuntimeException("AI章节审核失败: " + aiResp.getMessage());
                }
                result = aiResp.getData();
            } else {
                // 审核书籍
                BookAuditReqDto bookReq = BookAuditReqDto.builder()
                        .id(dto.getRelatedId())
                        .bookName(dto.getBookName())
                        .bookDesc(dto.getBookDesc())
                        .build();
                RestResp<BookAuditRespDto> aiResp = aiFeign.auditBook(bookReq);
                 if (!aiResp.isOk()) {
                     throw new RuntimeException("AI书籍审核失败: " + aiResp.getMessage());
                }
                result = aiResp.getData();
            }

            return RestResp.ok(result);
            
        } catch (Exception e) {
            // 3. AI服务失败，回滚积分
            log.error("AI审核服务调用失败，开始回滚积分，作者ID: {}, 错误: {}", authorId, e.getMessage(), e);
            RestResp<Void> rollbackResult = rollbackPoints(dto);
            if (!rollbackResult.isOk()) {
                log.error("积分回滚失败，作者ID: {}, 错误: {}", authorId, rollbackResult.getMessage());
                return RestResp.fail(ErrorCodeEnum.SYSTEM_ERROR, "AI服务调用失败，积分回滚也失败，请联系管理员");
            }
            return RestResp.fail(ErrorCodeEnum.SYSTEM_ERROR, "AI审核服务调用失败，积分已自动退回");
        }
    }

    @Override
    public RestResp<Object> polish(Long authorId, AuthorPointsConsumeReqDto dto) {
        dto.setAuthorId(authorId);
        dto.setConsumeType(1); // 1-AI润色
        dto.setConsumePoints(10); // 10点/次
        
        // 1. 先扣除积分
        RestResp<Void> deductResult = deductPoints(dto);
        if (!deductResult.isOk()) {
            return RestResp.fail(ErrorCodeEnum.USER_POINTS_NOT_ENOUGH, deductResult.getMessage());
        }
        
        // 2. 调用AI润色服务
        try {
            TextPolishReqDto polishReq = new TextPolishReqDto();
            polishReq.setSelectedText(dto.getContent());
            polishReq.setStyle(dto.getStyle());
            polishReq.setRequirement(dto.getRequirement());
            
            RestResp<TextPolishRespDto> aiResp = aiFeign.polishText(polishReq);
            if (!aiResp.isOk()) {
                throw new RuntimeException("AI润色失败: " + aiResp.getMessage());
            }
            
            return RestResp.ok(aiResp.getData());
            
        } catch (Exception e) {
            // 3. AI服务失败，回滚积分
            log.error("AI润色服务调用失败，开始回滚积分，作者ID: {}, 错误: {}", authorId, e.getMessage(), e);
            RestResp<Void> rollbackResult = rollbackPoints(dto);
            if (!rollbackResult.isOk()) {
                log.error("积分回滚失败，作者ID: {}, 错误: {}", authorId, rollbackResult.getMessage());
                return RestResp.fail(ErrorCodeEnum.SYSTEM_ERROR, "AI服务调用失败，积分回滚也失败，请联系管理员");
            }
            return RestResp.fail(ErrorCodeEnum.SYSTEM_ERROR, "AI润色服务调用失败，积分已自动退回");
        }
    }

    @Override
    public SseEmitter polishStream(Long authorId, AuthorPointsConsumeReqDto dto) {
        SseEmitter clientEmitter = new SseEmitter(300_000L);
        dto.setAuthorId(authorId);
        dto.setConsumeType(1);
        dto.setConsumePoints(10);

        RestResp<Void> deductResult = deductPoints(dto);
        if (!deductResult.isOk()) {
            try {
                clientEmitter.send(
                        SseEmitter.event()
                                .name("error")
                                .data(
                                        sseJson(
                                                ErrorCodeEnum.USER_POINTS_NOT_ENOUGH.getCode(),
                                                deductResult.getMessage())));
            } catch (IOException e) {
                log.warn("润色流式 SSE 发送扣分错误失败", e);
            }
            clientEmitter.complete();
            return clientEmitter;
        }

        TextPolishReqDto polishReq = new TextPolishReqDto();
        polishReq.setSelectedText(dto.getContent());
        polishReq.setStyle(dto.getStyle());
        polishReq.setRequirement(dto.getRequirement());

        String uri =
                "http://novel-ai-service"
                        + ApiRouterConsts.API_INNER_AI_URL_PREFIX
                        + "/polish/stream";

        AtomicBoolean rolledBack = new AtomicBoolean(false);
        AtomicBoolean clientCompleted = new AtomicBoolean(false);

        Runnable rollbackOnce =
                () -> {
                    if (rolledBack.compareAndSet(false, true)) {
                        RestResp<Void> rb = rollbackPoints(dto);
                        if (!rb.isOk()) {
                            log.error("流式润色回滚积分失败，作者ID: {}", authorId);
                        }
                    }
                };

        ParameterizedTypeReference<ServerSentEvent<String>> sseType = new ParameterizedTypeReference<>() {};

        Disposable disposable =
                aiInnerWebClient
                        .post()
                        .uri(uri)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(polishReq)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .retrieve()
                        .bodyToFlux(sseType)
                        .publishOn(Schedulers.boundedElastic())
                        .subscribe(
                                evt -> {
                                    String name = evt.event() != null ? evt.event() : "message";
                                    String data = evt.data() != null ? evt.data() : "";
                                    if ("error".equals(name)) {
                                        rollbackOnce.run();
                                    }
                                    try {
                                        clientEmitter.send(SseEmitter.event().name(name).data(data));
                                        if ("done".equals(name) && clientCompleted.compareAndSet(false, true)) {
                                            clientEmitter.complete();
                                        }
                                    } catch (IOException e) {
                                        log.warn("转发润色 SSE 失败", e);
                                        rollbackOnce.run();
                                        clientEmitter.completeWithError(e);
                                    }
                                },
                                err -> {
                                    log.error("润色流式上游异常，作者ID: {}", authorId, err);
                                    rollbackOnce.run();
                                    if (clientCompleted.compareAndSet(false, true)) {
                                        try {
                                            clientEmitter.send(
                                                    SseEmitter.event()
                                                            .name("error")
                                                            .data(
                                                                    sseJson(
                                                                            ErrorCodeEnum.SYSTEM_ERROR.getCode(),
                                                                            "AI润色服务暂时不可用，积分已退回")));
                                        } catch (IOException ex) {
                                            log.debug("发送流式错误事件失败", ex);
                                        }
                                        clientEmitter.completeWithError(err);
                                    }
                                },
                                () -> {
                                    if (clientCompleted.compareAndSet(false, true)) {
                                        clientEmitter.complete();
                                    }
                                });

        clientEmitter.onCompletion(disposable::dispose);
        clientEmitter.onTimeout(
                () -> {
                    disposable.dispose();
                    rollbackOnce.run();
                });
        clientEmitter.onError(
                e -> {
                    disposable.dispose();
                    rollbackOnce.run();
                });

        return clientEmitter;
    }

    private static String sseJson(String code, String message) {
        String safe =
                message == null
                        ? ""
                        : message.replace("\\", "\\\\")
                                .replace("\"", "\\\"")
                                .replace("\n", " ")
                                .replace("\r", " ");
        return "{\"code\":\"" + code + "\",\"message\":\"" + safe + "\"}";
    }

    @Override
    public RestResp<String> generateCoverPrompt(Long authorId, BookCoverReqDto reqDto) {
        log.info("生成封面提示词请求，作者ID: {}, 小说ID: {}, 小说名: {}", authorId, reqDto.getId(), reqDto.getBookName());
        try {
            // 注意：此接口不扣积分，仅调用AI服务生成提示词
            RestResp<String> promptResp = aiFeign.getBookCoverPrompt(reqDto);
            if (!promptResp.isOk()) {
                log.warn("生成封面提示词失败，作者ID: {}, 小说ID: {}, 错误: {}", authorId, reqDto.getId(), promptResp.getMessage());
                return RestResp.fail(ErrorCodeEnum.SYSTEM_ERROR, "生成封面提示词失败: " + promptResp.getMessage());
            }
            log.info("生成封面提示词成功，作者ID: {}, 小说ID: {}, 提示词长度: {}", authorId, reqDto.getId(), 
                    promptResp.getData() != null ? promptResp.getData().length() : 0);
            return RestResp.ok(promptResp.getData());
        } catch (Exception e) {
            log.error("生成封面提示词异常，作者ID: {}, 小说ID: {}", authorId, reqDto.getId(), e);
            return RestResp.fail(ErrorCodeEnum.SYSTEM_ERROR, "生成封面提示词失败，请稍后重试");
        }
    }

    @Override
    public RestResp<Object> generateCover(Long authorId, AuthorPointsConsumeReqDto dto) {
        dto.setAuthorId(authorId);
        dto.setConsumeType(2); // 2-AI封面
        dto.setConsumePoints(100); // 100点/次
        
        // 1. 先扣除积分
        RestResp<Void> deductResult = deductPoints(dto);
        if (!deductResult.isOk()) {
            return RestResp.fail(ErrorCodeEnum.USER_POINTS_NOT_ENOUGH, deductResult.getMessage());
        }
        
        // 2. 调用AI封面生成服务
        try {
            String prompt;
            if (dto.getCoverPrompt() != null && !dto.getCoverPrompt().isBlank()) {
                prompt = dto.getCoverPrompt().trim();
                log.info(
                        "封面生图：使用请求内携带的 coverPrompt，跳过重复 LLM，作者ID: {}, 小说ID: {}",
                        authorId,
                        dto.getRelatedId());
            } else {
                BookCoverReqDto coverReq =
                        BookCoverReqDto.builder()
                                .id(dto.getRelatedId())
                                .bookName(dto.getBookName())
                                .bookDesc(dto.getBookDesc())
                                .categoryName(dto.getCategoryName())
                                .build();

                RestResp<String> promptResp = aiFeign.getBookCoverPrompt(coverReq);
                if (!promptResp.isOk()) {
                    throw new RuntimeException("获取封面提示词失败: " + promptResp.getMessage());
                }
                prompt = promptResp.getData();
            }
            
            // 2.2 异步生图：立即返回 jobId，进度由前端轮询 getCoverJobStatus
            CoverImageAsyncSubmitReqDto asyncReq = new CoverImageAsyncSubmitReqDto();
            asyncReq.setPrompt(prompt);
            asyncReq.setAuthorId(authorId);
            asyncReq.setConsumeType(dto.getConsumeType());
            asyncReq.setConsumePoints(dto.getConsumePoints());
            asyncReq.setRelatedId(dto.getRelatedId());
            asyncReq.setRelatedDesc(dto.getRelatedDesc());
            asyncReq.setRequestId(dto.getRequestId());

            RestResp<ImageGenJobSubmitRespDto> submitResp = aiFeign.submitImageGenerationAsync(asyncReq);
            if (!submitResp.isOk()) {
                RestResp<Void> rb = rollbackPoints(dto);
                if (!rb.isOk()) {
                    log.error("提交异步生图失败后积分回滚失败，作者ID: {}", authorId);
                }
                return RestResp.fail(ErrorCodeEnum.SYSTEM_ERROR,
                        submitResp.getMessage() != null ? submitResp.getMessage() : "提交生图任务失败");
            }
            return RestResp.ok(submitResp.getData());

        } catch (Exception e) {
            // 3. AI服务失败，回滚积分
            log.error("AI封面生成服务调用失败，开始回滚积分，作者ID: {}, 错误: {}", authorId, e.getMessage(), e);
            RestResp<Void> rollbackResult = rollbackPoints(dto);
            if (!rollbackResult.isOk()) {
                log.error("积分回滚失败，作者ID: {}, 错误: {}", authorId, rollbackResult.getMessage());
                return RestResp.fail(ErrorCodeEnum.SYSTEM_ERROR, "AI服务调用失败，积分回滚也失败，请联系管理员");
            }
            return RestResp.fail(ErrorCodeEnum.SYSTEM_ERROR, "AI封面生成服务调用失败，积分已自动退回");
        }
    }

    @Override
    public RestResp<Object> getCoverJobStatus(Long authorId, String jobId) {
        if (jobId == null || jobId.isBlank()) {
            return RestResp.fail(ErrorCodeEnum.USER_REQUEST_PARAM_ERROR, "任务ID无效");
        }
        RestResp<ImageGenJobStatusRespDto> r = aiFeign.getImageGenJob(jobId);
        if (!r.isOk() || r.getData() == null) {
            return RestResp.fail(ErrorCodeEnum.USER_REQUEST_PARAM_ERROR,
                    r.getMessage() != null ? r.getMessage() : "查询任务失败");
        }
        ImageGenJobStatusRespDto data = r.getData();
        if (data.getAuthorId() == null || !data.getAuthorId().equals(authorId)) {
            return RestResp.fail(ErrorCodeEnum.USER_UN_AUTH);
        }
        return RestResp.ok(data);
    }


}
