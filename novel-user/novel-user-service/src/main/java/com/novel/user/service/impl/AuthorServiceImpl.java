package com.novel.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.novel.user.dao.entity.AuthorInfo;
import com.novel.user.dao.mapper.AuthorInfoMapper;
import com.novel.user.dto.AuthorInfoDto;
import com.novel.user.dto.mq.AuthorPointsConsumeMqDto;
import com.novel.user.dto.req.AuthorPointsConsumeReqDto;
import com.novel.user.dto.req.AuthorRegisterReqDto;
import com.novel.user.service.AuthorService;
import com.novel.user.service.CacheService;
import com.novel.user.service.MessageService;
import com.novel.user.feign.BookFeignManager;
import com.novel.user.dto.req.MessagePageReqDto;
import com.novel.user.dto.resp.MessageRespDto;
import com.novel.book.dto.req.*;
import com.novel.book.dto.resp.BookChapterRespDto;
import com.novel.book.dto.resp.BookInfoRespDto;
import com.novel.book.dto.mq.BookAddMqDto;
import com.novel.book.dto.mq.BookUpdateMqDto;
import com.novel.book.dto.mq.ChapterSubmitMqDto;
import com.novel.ai.feign.AiFeign;
import com.novel.book.dto.req.BookAuditReqDto;
import com.novel.book.dto.req.ChapterAuditReqDto;
import com.novel.book.dto.req.BookCoverReqDto;
import com.novel.ai.dto.req.TextPolishReqDto;
import com.novel.book.dto.resp.BookAuditRespDto;
import com.novel.book.dto.resp.ChapterAuditRespDto;
import com.novel.ai.dto.resp.TextPolishRespDto;
import com.novel.common.constant.AmqpConsts;
import com.novel.common.constant.CacheConsts;
import com.novel.common.constant.DatabaseConsts;
import com.novel.common.constant.ErrorCodeEnum;
import com.novel.common.req.PageReqDto;
import com.novel.common.resp.PageRespDto;
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
public class AuthorServiceImpl implements AuthorService {

    private final AuthorInfoMapper authorInfoMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final RocketMQTemplate rocketMQTemplate;
    private final BookFeignManager bookFeignManager;
    private final MessageService messageService;
    private final AiFeign aiFeign;
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
        
        // 从 Redis 读取最新积分（如果 Redis 中没有，会从数据库加载）
        Long authorId = authorInfoDto.getId();
        initPointsIfNeeded(authorId);
        
        // 检查并重置免费积分（每日重置），确保查询时看到的是最新状态
        resetFreePointsIfNeeded(authorId, LocalDate.now());
        
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

        // 保存使用的积分信息到dto，用于后续回滚
        dto.setUsedFreePoints(usedFreePoints);
        dto.setUsedPaidPoints(usedPaidPoints);

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
        
        // 2. 精确回滚：根据实际使用的免费积分和付费积分进行回滚
        // 优先回滚到付费积分，再回滚到免费积分（与扣除顺序相反）
        Integer usedFreePoints = dto.getUsedFreePoints();
        Integer usedPaidPoints = dto.getUsedPaidPoints();
        
        // 如果没有记录使用的积分信息，使用旧逻辑（全部回滚到免费积分）
        if (usedFreePoints == null && usedPaidPoints == null) {
            log.warn("作者[{}]回滚积分时缺少使用记录，使用默认回滚策略（全部回滚到免费积分）", authorId);
            usedFreePoints = consumePoints;
            usedPaidPoints = 0;
        } else {
            // 确保值不为null
            if (usedFreePoints == null) usedFreePoints = 0;
            if (usedPaidPoints == null) usedPaidPoints = 0;
        }
        
        try {
            // 先回滚付费积分
            if (usedPaidPoints > 0) {
                Long newPaid = stringRedisTemplate.opsForValue()
                    .increment(getPaidPointsKey(authorId), usedPaidPoints);
                if (newPaid == null) {
                    log.error("作者[{}]回滚付费积分失败，Redis操作返回null", authorId);
                    return RestResp.fail(ErrorCodeEnum.SYSTEM_ERROR);
                }
                log.debug("作者[{}]回滚付费积分成功，回滚点数: {}, 当前付费积分: {}", 
                    authorId, usedPaidPoints, newPaid);
            }
            
            // 再回滚免费积分
            if (usedFreePoints > 0) {
                Long newFree = stringRedisTemplate.opsForValue()
                    .increment(getFreePointsKey(authorId), usedFreePoints);
                if (newFree == null) {
                    log.error("作者[{}]回滚免费积分失败，Redis操作返回null", authorId);
                    return RestResp.fail(ErrorCodeEnum.SYSTEM_ERROR);
                }
                log.debug("作者[{}]回滚免费积分成功，回滚点数: {}, 当前免费积分: {}", 
                    authorId, usedFreePoints, newFree);
            }
            
            log.info("作者[{}]积分回滚成功，回滚免费积分: {}, 回滚付费积分: {}", 
                authorId, usedFreePoints, usedPaidPoints);
        } catch (Exception e) {
            log.error("作者[{}]回滚积分失败，回滚免费积分: {}, 回滚付费积分: {}", 
                authorId, usedFreePoints, usedPaidPoints, e);
            return RestResp.fail(ErrorCodeEnum.SYSTEM_ERROR);
        }

        // 3. 发送 MQ 消息，异步持久化回滚记录到数据库
        LocalDate today = LocalDate.now();
        try {
            AuthorPointsConsumeMqDto mqDto = AuthorPointsConsumeMqDto.builder()
                .authorId(authorId)
                .consumeType(dto.getConsumeType())
                .consumePoints(consumePoints)
                .usedFreePoints(usedFreePoints != null ? usedFreePoints : consumePoints) // 使用实际使用的免费积分
                .usedPaidPoints(usedPaidPoints != null ? usedPaidPoints : 0) // 使用实际使用的付费积分
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
            
            // 【新增】同步更新数据库，确保 Redis 丢失后数据依然正确
            AuthorInfo update = new AuthorInfo();
            update.setId(authorId);
            update.setFreePoints(500);
            // 这里只更新免费积分和时间，不影响付费积分
            update.setFreePointsUpdateTime(LocalDateTime.now());
            authorInfoMapper.updateById(update);
            
            log.debug("作者[{}]免费积分已重置为500 (Redis + DB)", authorId);
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
        BookAddMqDto mqDto = BookAddMqDto.builder()
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
            String destination = AmqpConsts.BookAddMq.TOPIC + ":" + AmqpConsts.BookAddMq.TAG_ADD;
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
        BookUpdateMqDto mqDto = BookUpdateMqDto.builder()
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
            String destination = AmqpConsts.BookUpdateMq.TOPIC + ":" + AmqpConsts.BookUpdateMq.TAG_UPDATE;
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
            // 2.1 获取提示词
            BookCoverReqDto coverReq = BookCoverReqDto.builder()
                    .id(dto.getRelatedId())
                    .bookName(dto.getBookName())
                    .bookDesc(dto.getBookDesc())
                    .categoryName(dto.getCategoryName())
                    .build();
            
            RestResp<String> promptResp = aiFeign.getBookCoverPrompt(coverReq);
            if (!promptResp.isOk()) {
                throw new RuntimeException("获取封面提示词失败: " + promptResp.getMessage());
            }
            String prompt = promptResp.getData();
            
            // 2.2 生成图片
            RestResp<String> imageResp = aiFeign.generateImage(prompt);
            if (!imageResp.isOk()) {
                throw new RuntimeException("图片生成失败: " + imageResp.getMessage());
            }
            
            return RestResp.ok(imageResp.getData());
            
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



}
