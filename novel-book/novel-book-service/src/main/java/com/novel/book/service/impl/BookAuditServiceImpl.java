package com.novel.book.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.novel.book.dao.entity.BookChapter;
import com.novel.book.dao.entity.BookInfo;
import com.novel.book.dao.entity.ContentAudit;
import com.novel.book.dao.mapper.BookChapterMapper;
import com.novel.book.dao.mapper.BookInfoMapper;
import com.novel.book.dao.mapper.ContentAuditMapper;
import com.novel.book.dto.mq.BookAuditResultMqDto;
import com.novel.book.dto.mq.ChapterAuditResultMqDto;
import com.novel.book.dto.resp.BookAuditRespDto;
import com.novel.book.dto.resp.ChapterAuditRespDto;
import com.novel.book.feign.UserFeignManager;
import com.novel.book.service.BookAuditService;
import com.novel.book.service.BookExistBloomService;
import com.novel.common.constant.AmqpConsts;
import com.novel.common.constant.CacheConsts;
import com.novel.common.constant.DatabaseConsts;
import com.novel.common.constant.ErrorCodeEnum;
import com.novel.common.resp.RestResp;
import com.novel.user.dto.req.MessageSendReqDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.novel.ai.feign.AiFeign;
import com.novel.ai.dto.req.AuditRuleReqDto;
import com.novel.ai.dto.resp.AuditRuleRespDto;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 书籍审核服务实现类（包含AI审核和人工审核）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookAuditServiceImpl implements BookAuditService {

    private final BookInfoMapper bookInfoMapper;
    private final BookChapterMapper bookChapterMapper;
    private final ContentAuditMapper contentAuditMapper;
    private final RocketMQTemplate rocketMQTemplate; // 添加MQ依赖
    private final UserFeignManager userFeignManager; // 添加用户服务依赖，用于发送消息
    private final StringRedisTemplate stringRedisTemplate; // 添加Redis依赖，用于清除缓存
    private final AiFeign aiFeign; // 添加AI服务依赖，用于提取审核规则
    private final BookExistBloomService bookExistBloomService;

    /**
     * AI审核置信度阈值，低于此值需要人工审核
     */
    private static final BigDecimal CONFIDENCE_THRESHOLD = new BigDecimal("0.8");

    /**
     * 数据来源：小说基本信息表
     */
    private static final Integer DATA_SOURCE_BOOK_INFO = 0;

    /**
     * 数据来源：小说章节表
     */
    private static final Integer DATA_SOURCE_BOOK_CHAPTER = 1;

    /**
     * 审核状态：待审核
     */
    private static final Integer AUDIT_STATUS_PENDING = 0;

    /**
     * 审核状态：通过
     */
    private static final Integer AUDIT_STATUS_PASSED = 1;

    /**
     * 审核状态：不通过
     */
    private static final Integer AUDIT_STATUS_REJECTED = 2;

    /** 与表 content_audit.audit_reason 列 varchar(500) 一致 */
    private static final int CONTENT_AUDIT_AUDIT_REASON_MAX_LEN = 500;

    /**
     * 同一数据源可有多条审核记录，业务上取最新一条
     */
    private ContentAudit findLatestContentAuditBySource(Integer sourceType, Long sourceId) {
        QueryWrapper<ContentAudit> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("source_type", sourceType)
                .eq("source_id", sourceId)
                .orderByDesc("id")
                .last("LIMIT 1");
        return contentAuditMapper.selectOne(queryWrapper);
    }

    /**
     * 事务提交后再执行（避免 MQ/Redis/Feign 占用数据库连接）。无活跃事务时立即执行。
     */
    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        action.run();
                    } catch (Exception e) {
                        log.error("事务提交后回调执行失败", e);
                    }
                }
            });
        } else {
            action.run();
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void processChapterAuditResult(ChapterAuditResultMqDto resultDto) {
        Long chapterId = resultDto.getChapterId();
        log.info("收到章节审核结果MQ消息，taskId: {}, chapterId: {}, auditStatus: {}, success: {}", 
                resultDto.getTaskId(), chapterId, resultDto.getAuditStatus(), resultDto.getSuccess());

        // 1. 查询章节信息
        BookChapter bookChapter = bookChapterMapper.selectById(chapterId);
        if (bookChapter == null) {
            log.warn("章节不存在，忽略审核结果，chapterId: {}", chapterId);
            return;
        }

        // 2. 如果AI处理失败，更新审核表记录失败原因
        if (!Boolean.TRUE.equals(resultDto.getSuccess())) {
            try {
                ContentAudit existingAudit = findLatestContentAuditBySource(DATA_SOURCE_BOOK_CHAPTER, chapterId);
                if (existingAudit != null) {
                    updateAuditRecordOnFailure(existingAudit.getId(),
                            resultDto.getErrorMessage() != null ? resultDto.getErrorMessage() : "AI审核处理失败");
                }
            } catch (Exception e) {
                log.error("更新审核记录失败，章节ID: {}", chapterId, e);
            }
            return;
        }

        // 3. 先查询是否已存在审核记录，如果存在则更新，不存在则插入
        ContentAudit initialAudit = null;
        try {
            initialAudit = findLatestContentAuditBySource(DATA_SOURCE_BOOK_CHAPTER, chapterId);
        } catch (Exception e) {
            log.warn("查询审核记录失败，章节ID: {}，将创建新记录。错误: {}", chapterId, e.getMessage());
        }
        log.info("[AI-Audit] 章节[{}] 已存在 ContentAudit: {}", chapterId,
                initialAudit != null ? ("id=" + initialAudit.getId() + ",status=" + initialAudit.getAuditStatus()) : "无");

        // 如果不存在审核记录，创建一条（待审核状态）
        if (initialAudit == null) {
            initialAudit = ContentAudit.builder()
                    .dataSource(DATA_SOURCE_BOOK_CHAPTER)
                    .dataSourceId(chapterId)
                    .contentText(bookChapter.getChapterName() + " " + bookChapter.getContent())
                    .auditStatus(AUDIT_STATUS_PENDING) // 初始状态为待审核
                    .aiConfidence(null)
                    .auditReason(null)
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .build();
            try {
                int inserted = contentAuditMapper.insert(initialAudit);
                log.info("[AI-Audit] 章节[{}] 新建 ContentAudit 成功，影响行数: {}, 回填 id: {}",
                        chapterId, inserted, initialAudit.getId());
            } catch (Exception e) {
                log.error("[AI-Audit] 章节[{}] 新建 ContentAudit 失败", chapterId, e);
            }
        }

        // 4. 构建ChapterAuditRespDto（用于兼容现有的处理逻辑）
        ChapterAuditRespDto aiResult = ChapterAuditRespDto.builder()
                .bookId(resultDto.getBookId())
                .chapterNum(bookChapter.getChapterNum())
                .auditStatus(resultDto.getAuditStatus())
                .aiConfidence(resultDto.getAiConfidence())
                .auditReason(resultDto.getAuditReason())
                .build();

        Integer auditStatus = resultDto.getAuditStatus();
        BigDecimal aiConfidence = resultDto.getAiConfidence();
        String fullReason = resultDto.getAuditReason();

        log.info("章节[{}]处理AI审核结果，auditStatus: {}, aiConfidence: {}, reason: {}", 
                chapterId, auditStatus, aiConfidence, 
                fullReason != null && fullReason.length() > 100 ? fullReason.substring(0, 100) + "..." : fullReason);

        // 5. 判断审核结果类型
        boolean isPending = AUDIT_STATUS_PENDING.equals(auditStatus);
        boolean isPassed = AUDIT_STATUS_PASSED.equals(auditStatus);
        boolean isRejected = AUDIT_STATUS_REJECTED.equals(auditStatus);
        boolean isHighConfidence = aiConfidence != null &&
                aiConfidence.compareTo(CONFIDENCE_THRESHOLD) >= 0;

        Long contentAuditId = initialAudit != null ? initialAudit.getId() : null;
        if (contentAuditId == null) {
            log.warn("[AI-Audit] 章节[{}] ContentAudit id 为空，后续更新将被跳过", chapterId);
        }

        // 6. 更新审核表和章节表
        if (isPassed && isHighConfidence) {
            // 情况1：AI审核通过且置信度高 -> 更新审核表和章节表
            if (contentAuditId != null) {
                try {
                    int rows = updateContentAuditFromAiResult(contentAuditId,
                            AUDIT_STATUS_PASSED, aiResult.getAiConfidence(), aiResult.getAuditReason());
                    log.info("[AI-Audit] 章节[{}] ContentAudit[{}] 更新为 PASSED，影响行数: {}", chapterId, contentAuditId, rows);
                } catch (Exception e) {
                    log.error("[AI-Audit] 章节[{}] 更新 ContentAudit 失败", chapterId, e);
                }
            }

            try {
                updateChapterDirectly(chapterId, AUDIT_STATUS_PASSED, null);
                log.info("[AI-Audit] 章节[{}] BookChapter 更新为 PASSED", chapterId);
            } catch (Exception e) {
                log.error("[AI-Audit] 章节[{}] 更新 BookChapter 失败", chapterId, e);
            }
            
            // 审核通过后，更新bookInfo表的最新章节信息
            try {
                updateBookInfoLastChapter(bookChapter.getBookId());
                log.debug("书籍[{}]最新章节信息已更新", bookChapter.getBookId());
                addBookToBloomIfEligible(bookChapter.getBookId());
            } catch (Exception e) {
                log.error("更新书籍最新章节信息失败，书籍ID: {}", bookChapter.getBookId(), e);
            }

            final Long passBookId = bookChapter.getBookId();
            final Integer passChapterNum = bookChapter.getChapterNum();
            runAfterCommit(() -> {
                try {
                    stringRedisTemplate.delete(CacheConsts.BOOK_CHAPTER_CACHE_NAME + "::" + passBookId);
                    log.debug("已清除书籍章节目录缓存，bookId: {}", passBookId);
                } catch (Exception e) {
                    log.warn("清除章节目录缓存失败，bookId: {}", passBookId, e);
                }
                try {
                    stringRedisTemplate.delete(CacheConsts.BOOK_CONTENT_CACHE_NAME + "::content:" + passBookId + ":" + passChapterNum);
                    log.debug("已清除章节内容缓存，bookId: {}, chapterNum: {}", passBookId, passChapterNum);
                } catch (Exception e) {
                    log.warn("清除章节内容缓存失败，bookId: {}, chapterNum: {}", passBookId, passChapterNum, e);
                }
            });

            sendBookChangeMsg(bookChapter.getBookId());

            BookInfo bookInfo = bookInfoMapper.selectById(bookChapter.getBookId());
            if (bookInfo != null) {
                sendAuditPassMessageToAuthor(bookChapter.getBookId(),
                        bookInfo.getBookName() + " - " + bookChapter.getChapterName(), false);
            }
            
            log.info("章节[{}]AI审核通过，置信度: {}，已更新审核表和章节表", chapterId, aiConfidence);

        } else if (isPassed && !isHighConfidence) {
            // 情况2：AI审核通过但置信度低 -> 更新审核表为待人工审核，章节表保持待审核状态
            if (contentAuditId != null) {
                try {
                    int rows = updateContentAuditFromAiResult(contentAuditId,
                            AUDIT_STATUS_PENDING, aiResult.getAiConfidence(), aiResult.getAuditReason());
                    log.info("[AI-Audit] 章节[{}] AI通过但置信度低[{}]，ContentAudit[{}] 更新为 PENDING，影响行数: {}",
                            chapterId, aiConfidence, contentAuditId, rows);
                } catch (Exception e) {
                    log.error("[AI-Audit] 章节[{}] 更新 ContentAudit 失败", chapterId, e);
                }
            }

        } else if (isRejected) {
            // 情况3：AI审核不通过 -> 更新审核表和章节表为不通过
            String shortReason = extractShortReason(fullReason, aiConfidence);
            if (contentAuditId != null) {
                try {
                    int rows = updateContentAuditFromAiResult(contentAuditId,
                            AUDIT_STATUS_REJECTED, aiResult.getAiConfidence(), aiResult.getAuditReason());
                    log.info("[AI-Audit] 章节[{}] ContentAudit[{}] 更新为 REJECTED，影响行数: {}", chapterId, contentAuditId, rows);
                } catch (Exception e) {
                    log.error("[AI-Audit] 章节[{}] 更新 ContentAudit 失败", chapterId, e);
                }
            }

            try {
                updateChapterDirectly(chapterId, AUDIT_STATUS_REJECTED, shortReason);
                log.info("[AI-Audit] 章节[{}] BookChapter 更新为 REJECTED", chapterId);
            } catch (Exception e) {
                log.error("[AI-Audit] 章节[{}] 更新 BookChapter 失败", chapterId, e);
            }

            final Long rejectBookId = bookChapter.getBookId();
            final Integer rejectChapterNum = bookChapter.getChapterNum();
            runAfterCommit(() -> {
                try {
                    stringRedisTemplate.delete(CacheConsts.BOOK_CONTENT_CACHE_NAME + "::content:" + rejectBookId + ":" + rejectChapterNum);
                    log.info("AI审核不通过，已删除章节内容缓存，bookId: {}, chapterNum: {}", rejectBookId, rejectChapterNum);
                } catch (Exception e) {
                    log.warn("删除章节内容缓存失败，bookId: {}, chapterNum: {}", rejectBookId, rejectChapterNum, e);
                }
            });

            log.warn("章节[{}]AI审核不通过，原因: {}，已更新审核表和章节表", chapterId, shortReason);

        } else {
            // 情况4（pending 或未知状态）：AI 未得出确定结论，保持待人工审核
            if (!isPending) {
                log.warn("[AI-Audit] 章节[{}] 收到未知 auditStatus={}，按 PENDING 处理", chapterId, auditStatus);
            }
            String shortReason = extractShortReason(fullReason, aiConfidence);
            if (contentAuditId != null) {
                try {
                    int rows = updateContentAuditFromAiResult(contentAuditId,
                            AUDIT_STATUS_PENDING, aiResult.getAiConfidence(), aiResult.getAuditReason());
                    log.info("[AI-Audit] 章节[{}] ContentAudit[{}] 更新为 PENDING（待人工），影响行数: {}",
                            chapterId, contentAuditId, rows);
                } catch (Exception e) {
                    log.error("[AI-Audit] 章节[{}] 更新 ContentAudit 失败", chapterId, e);
                }
            }
            log.warn("章节[{}]AI未得出确定结论(status={})，原因: {}，已记录待人工审核", chapterId, auditStatus, shortReason);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void processBookAuditResult(BookAuditResultMqDto resultDto) {
        Long bookId = resultDto.getBookId();
        log.info("收到书籍审核结果MQ消息，taskId: {}, bookId: {}, auditStatus: {}, success: {}", 
                resultDto.getTaskId(), bookId, resultDto.getAuditStatus(), resultDto.getSuccess());

        // 1. 查询书籍信息
        BookInfo bookInfo = bookInfoMapper.selectById(bookId);
        if (bookInfo == null) {
            log.warn("书籍不存在，忽略审核结果，bookId: {}", bookId);
            return;
        }

        // 2. 如果AI处理失败，更新审核表记录失败原因
        if (!Boolean.TRUE.equals(resultDto.getSuccess())) {
            try {
                ContentAudit existingAudit = findLatestContentAuditBySource(DATA_SOURCE_BOOK_INFO, bookId);
                if (existingAudit != null) {
                    updateAuditRecordOnFailure(existingAudit.getId(),
                            resultDto.getErrorMessage() != null ? resultDto.getErrorMessage() : "AI审核处理失败");
                }
            } catch (Exception e) {
                log.error("更新审核记录失败，书籍ID: {}", bookId, e);
            }
            return;
        }

        // 3. 先查询是否已存在审核记录，如果存在则更新，不存在则插入
        ContentAudit initialAudit = null;
        try {
            initialAudit = findLatestContentAuditBySource(DATA_SOURCE_BOOK_INFO, bookId);
        } catch (Exception e) {
            log.warn("查询审核记录失败，书籍ID: {}，将创建新记录。错误: {}", bookId, e.getMessage());
        }
        log.info("[AI-Audit] 书籍[{}] 已存在 ContentAudit: {}", bookId,
                initialAudit != null ? ("id=" + initialAudit.getId() + ",status=" + initialAudit.getAuditStatus()) : "无");

        // 如果不存在审核记录，创建一条（待审核状态）
        if (initialAudit == null) {
            initialAudit = ContentAudit.builder()
                    .dataSource(DATA_SOURCE_BOOK_INFO)
                    .dataSourceId(bookId)
                    .contentText(bookInfo.getBookName() + " " + bookInfo.getBookDesc())
                    .auditStatus(AUDIT_STATUS_PENDING) // 初始状态为待审核
                    .aiConfidence(null)
                    .auditReason(null)
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .build();
            try {
                int inserted = contentAuditMapper.insert(initialAudit);
                log.info("[AI-Audit] 书籍[{}] 新建 ContentAudit 成功，影响行数: {}, 回填 id: {}",
                        bookId, inserted, initialAudit.getId());
            } catch (Exception e) {
                log.error("[AI-Audit] 书籍[{}] 新建 ContentAudit 失败", bookId, e);
            }
        }

        // 4. 构建BookAuditRespDto（用于兼容现有的处理逻辑）
        BookAuditRespDto aiResult = BookAuditRespDto.builder()
                .id(bookId)
                .auditStatus(resultDto.getAuditStatus())
                .aiConfidence(resultDto.getAiConfidence())
                .auditReason(resultDto.getAuditReason())
                .build();

        Integer auditStatus = resultDto.getAuditStatus();
        BigDecimal aiConfidence = resultDto.getAiConfidence();
        String fullReason = resultDto.getAuditReason();

        log.info("书籍[{}]处理AI审核结果，auditStatus: {}, aiConfidence: {}, reason: {}", 
                bookId, auditStatus, aiConfidence, 
                fullReason != null && fullReason.length() > 100 ? fullReason.substring(0, 100) + "..." : fullReason);

        // 5. 判断审核结果类型
        boolean isPending = AUDIT_STATUS_PENDING.equals(auditStatus);
        boolean isPassed = AUDIT_STATUS_PASSED.equals(auditStatus);
        boolean isRejected = AUDIT_STATUS_REJECTED.equals(auditStatus);
        boolean isHighConfidence = aiConfidence != null &&
                aiConfidence.compareTo(CONFIDENCE_THRESHOLD) >= 0;

        Long contentAuditId = initialAudit != null ? initialAudit.getId() : null;
        if (contentAuditId == null) {
            log.warn("[AI-Audit] 书籍[{}] ContentAudit id 为空，后续更新将被跳过（建议检查 content_audit 表结构或主键自增）", bookId);
        }

        // 6. 更新审核表和书籍表
        if (isPassed && isHighConfidence) {
            // 情况1：AI审核通过且置信度高 -> 更新审核表和书籍表
            if (contentAuditId != null) {
                try {
                    int rows = updateContentAuditFromAiResult(contentAuditId,
                            AUDIT_STATUS_PASSED, aiResult.getAiConfidence(), aiResult.getAuditReason());
                    log.info("[AI-Audit] 书籍[{}] ContentAudit[{}] 更新为 PASSED，影响行数: {}", bookId, contentAuditId, rows);
                } catch (Exception e) {
                    log.error("[AI-Audit] 书籍[{}] 更新 ContentAudit 失败", bookId, e);
                }
            }
            
            try {
                updateBookFromAudit(bookId, AUDIT_STATUS_PASSED, null);
                log.info("[AI-Audit] 书籍[{}] BookInfo 更新为 PASSED", bookId);
            } catch (Exception e) {
                log.error("[AI-Audit] 书籍[{}] 更新 BookInfo 失败", bookId, e);
            }
            
            log.info("书籍[{}]AI审核通过，置信度: {}，已更新审核表和书籍表", bookId, aiConfidence);

            sendBookChangeMsg(bookId);
            sendAuditPassMessageToAuthor(bookId, bookInfo.getBookName(), true);
            addBookToBloomIfEligible(bookId);

        } else if (isPassed && !isHighConfidence) {
            // 情况2：AI审核通过但置信度低 -> 更新审核表为待人工审核，书籍表保持待审核状态
            if (contentAuditId != null) {
                try {
                    int rows = updateContentAuditFromAiResult(contentAuditId,
                            AUDIT_STATUS_PENDING, aiResult.getAiConfidence(), aiResult.getAuditReason());
                    log.info("[AI-Audit] 书籍[{}] AI通过但置信度低[{}]，ContentAudit[{}] 更新为 PENDING，影响行数: {}",
                            bookId, aiConfidence, contentAuditId, rows);
                } catch (Exception e) {
                    log.error("[AI-Audit] 书籍[{}] 更新 ContentAudit 失败", bookId, e);
                }
            }

        } else if (isRejected) {
            // 情况3：AI审核不通过 -> 更新审核表和书籍表为不通过
            String shortReason = extractShortReason(fullReason, aiConfidence);
            if (contentAuditId != null) {
                try {
                    int rows = updateContentAuditFromAiResult(contentAuditId,
                            AUDIT_STATUS_REJECTED, aiResult.getAiConfidence(), aiResult.getAuditReason());
                    log.info("[AI-Audit] 书籍[{}] ContentAudit[{}] 更新为 REJECTED，影响行数: {}", bookId, contentAuditId, rows);
                } catch (Exception e) {
                    log.error("[AI-Audit] 书籍[{}] 更新 ContentAudit 失败", bookId, e);
                }
            }

            try {
                updateBookFromAudit(bookId, AUDIT_STATUS_REJECTED, shortReason);
                log.info("[AI-Audit] 书籍[{}] BookInfo 更新为 REJECTED", bookId);
            } catch (Exception e) {
                log.error("[AI-Audit] 书籍[{}] 更新 BookInfo 失败", bookId, e);
            }

            log.warn("书籍[{}]AI审核不通过，原因: {}，已更新审核表和书籍表", bookId, shortReason);

        } else {
            // 情况4（pending 或未知状态）：AI 未得出确定结论（异常兜底 / 请求为空 / 置信度低等）
            //  - ContentAudit 保持 PENDING 并记录原因，供人工审核查看
            //  - BookInfo.audit_status 维持原值（不强制改为 REJECTED），仅写入 reason 方便前端提示
            if (!isPending) {
                log.warn("[AI-Audit] 书籍[{}] 收到未知 auditStatus={}，按 PENDING 处理", bookId, auditStatus);
            }
            String shortReason = extractShortReason(fullReason, aiConfidence);
            if (contentAuditId != null) {
                try {
                    int rows = updateContentAuditFromAiResult(contentAuditId,
                            AUDIT_STATUS_PENDING, aiResult.getAiConfidence(), aiResult.getAuditReason());
                    log.info("[AI-Audit] 书籍[{}] ContentAudit[{}] 更新为 PENDING（待人工），影响行数: {}", bookId, contentAuditId, rows);
                } catch (Exception e) {
                    log.error("[AI-Audit] 书籍[{}] 更新 ContentAudit 失败", bookId, e);
                }
            }

            try {
                updateBookAuditReasonOnly(bookId, shortReason);
                log.info("[AI-Audit] 书籍[{}] BookInfo 审核原因已写入，audit_status 保持不变", bookId);
            } catch (Exception e) {
                log.error("[AI-Audit] 书籍[{}] 更新 BookInfo 原因失败", bookId, e);
            }

            log.warn("书籍[{}]AI未得出确定结论(status={})，原因: {}，已记录待人工审核", bookId, auditStatus, shortReason);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RestResp<Void> manualAudit(Long auditId, Integer auditStatus, String auditReason) {
        // 1. 查询审核记录
        ContentAudit audit = contentAuditMapper.selectById(auditId);
        if (audit == null) {
            return RestResp.fail(ErrorCodeEnum.AUDIT_RECORD_NOT_EXIST);
        }

        // 2. 校验审核状态
        if (!AUDIT_STATUS_PASSED.equals(auditStatus) &&
                !AUDIT_STATUS_REJECTED.equals(auditStatus)) {
            return RestResp.fail(ErrorCodeEnum.AUDIT_STATUS_PARAM_ERROR);
        }

        // 3. 更新审核表（主键 id）
        audit.setAuditStatus(auditStatus);
        audit.setIsHumanFinal(1); // 标记为人工最终裁决
        audit.setAuditReason(truncateForContentAuditAuditReason(auditReason));
        audit.setUpdateTime(LocalDateTime.now()); // 使用 update_time 而不是 audit_time

        final Long contentAuditRowId = audit.getId();
        final String contentTextSnapshot = audit.getContentText();
        
        // 异步调用 AI 提取审核规则
        if (contentTextSnapshot != null && !contentTextSnapshot.trim().isEmpty()) {
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    log.info("开始异步提取审核规则，auditId: {}", auditId);
                    AuditRuleReqDto ruleReq = new AuditRuleReqDto();
                    ruleReq.setContentText(contentTextSnapshot);
                    ruleReq.setAuditStatus(auditStatus);
                    ruleReq.setAuditReason(auditReason);
                    
                    RestResp<AuditRuleRespDto> ruleResp = aiFeign.extractAuditRule(ruleReq);
                    if (ruleResp.isOk() && ruleResp.getData() != null) {
                        AuditRuleRespDto ruleData = ruleResp.getData();
                        
                        // 更新回数据库
                        ContentAudit updateRuleAudit = new ContentAudit();
                        updateRuleAudit.setViolationLabel(ruleData.getViolationLabel());
                        updateRuleAudit.setKeySnippet(ruleData.getKeySnippet());
                        updateRuleAudit.setAuditRule(ruleData.getAuditRule());
                        
                        QueryWrapper<ContentAudit> ruleUpdateWrapper = new QueryWrapper<>();
                        ruleUpdateWrapper.eq("id", contentAuditRowId);
                        contentAuditMapper.update(updateRuleAudit, ruleUpdateWrapper);
                        
                        log.info("异步提取审核规则成功，auditId: {}, label: {}", auditId, ruleData.getViolationLabel());
                    } else {
                        log.warn("异步提取审核规则失败，auditId: {}, 响应: {}", auditId, ruleResp.getMessage());
                    }
                } catch (Exception e) {
                    log.error("异步提取审核规则异常，auditId: {}", auditId, e);
                }
            });
        }
        
        contentAuditMapper.updateById(audit);

        // 4. 根据数据来源同步更新对应的业务表
        if (DATA_SOURCE_BOOK_INFO.equals(audit.getDataSource())) {
            // 更新书籍表
            updateBookFromAudit(audit.getDataSourceId(), auditStatus, auditReason);
            
            if (AUDIT_STATUS_PASSED.equals(auditStatus)) {
                sendBookChangeMsg(audit.getDataSourceId());
                BookInfo bookInfoRow = bookInfoMapper.selectById(audit.getDataSourceId());
                if (bookInfoRow != null) {
                    sendAuditPassMessageToAuthor(bookInfoRow.getId(), bookInfoRow.getBookName(), true);
                }
                addBookToBloomIfEligible(audit.getDataSourceId());
            }
        } else if (DATA_SOURCE_BOOK_CHAPTER.equals(audit.getDataSource())) {
            // 更新章节表
            updateChapterFromAudit(audit.getDataSourceId(), auditStatus, auditReason);
            
            // 如果审核通过，更新bookInfo表的最新章节信息并发送ES消息
            BookChapter chapter = bookChapterMapper.selectById(audit.getDataSourceId());
            if (AUDIT_STATUS_PASSED.equals(auditStatus)) {
                if (chapter != null) {
                    try {
                        updateBookInfoLastChapter(chapter.getBookId());
                        log.info("人工审核通过，章节[{}]已更新书籍最新章节信息", audit.getDataSourceId());
                        addBookToBloomIfEligible(chapter.getBookId());
                    } catch (Exception e) {
                        log.error("更新书籍最新章节信息失败，章节ID: {}", audit.getDataSourceId(), e);
                    }

                    final Long manBookId = chapter.getBookId();
                    final Integer manChapterNum = chapter.getChapterNum();
                    runAfterCommit(() -> {
                        try {
                            stringRedisTemplate.delete(CacheConsts.BOOK_CHAPTER_CACHE_NAME + "::" + manBookId);
                            log.info("人工审核通过，已清除书籍章节目录缓存，bookId: {}", manBookId);
                        } catch (Exception e) {
                            log.warn("清除章节目录缓存失败，bookId: {}", manBookId, e);
                        }
                        try {
                            stringRedisTemplate.delete(CacheConsts.BOOK_CONTENT_CACHE_NAME + "::content:" + manBookId + ":" + manChapterNum);
                            log.info("人工审核通过，已清除章节内容缓存，bookId: {}, chapterNum: {}", manBookId, manChapterNum);
                        } catch (Exception e) {
                            log.warn("清除章节内容缓存失败，bookId: {}, chapterNum: {}", manBookId, manChapterNum, e);
                        }
                    });

                    sendBookChangeMsg(chapter.getBookId());

                    BookInfo bookInfo = bookInfoMapper.selectById(chapter.getBookId());
                    if (bookInfo != null) {
                        sendAuditPassMessageToAuthor(chapter.getBookId(),
                                bookInfo.getBookName() + " - " + chapter.getChapterName(), false);
                    }
                }
            } else {
                if (chapter != null) {
                    final Long failBookId = chapter.getBookId();
                    final Integer failChapterNum = chapter.getChapterNum();
                    runAfterCommit(() -> {
                        try {
                            stringRedisTemplate.delete(CacheConsts.BOOK_CONTENT_CACHE_NAME + "::content:" + failBookId + ":" + failChapterNum);
                            log.info("人工审核不通过，已删除章节内容缓存，bookId: {}, chapterNum: {}", failBookId, failChapterNum);
                        } catch (Exception e) {
                            log.warn("删除章节内容缓存失败，bookId: {}, chapterNum: {}", failBookId, failChapterNum, e);
                        }
                    });
                }
            }
        }

        log.info("人工审核完成，审核记录ID: {}，审核结果: {}", auditId,
                AUDIT_STATUS_PASSED.equals(auditStatus) ? "通过" : "不通过");

        return RestResp.ok();
    }

    /**
     * 直接更新章节表
     */
    private void updateChapterDirectly(Long chapterId, Integer auditStatus, String rejectReason) {
        log.info("updateChapterDirectly 开始执行，chapterId: {}, auditStatus: {}, rejectReason: {}", 
                chapterId, auditStatus, rejectReason);
        
        // 使用 UpdateWrapper 确保正确更新，包括 null 值
        UpdateWrapper<BookChapter> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", chapterId)
                .set("audit_status", auditStatus)
                .set("audit_reason", rejectReason)  // 明确设置，包括 null
                .set("update_time", LocalDateTime.now());
        
        int updateCount = bookChapterMapper.update(null, updateWrapper);
        log.info("updateChapterDirectly 执行完成，chapterId: {}, 更新行数: {}", chapterId, updateCount);
        if (updateCount == 0) {
            log.warn("警告：章节ID {} 更新行数为0，可能章节不存在或已被删除", chapterId);
        } else {
            log.info("章节ID {} 审核状态已更新为: {}", chapterId, auditStatus);
        }
    }

    /**
     * 根据主键 id 更新 AI 审核结果字段
     * @return 影响行数（0 表示目标记录不存在或条件未命中）
     */
    private int updateContentAuditFromAiResult(Long contentAuditId, Integer auditStatus,
            BigDecimal aiConfidence, String auditReason) {
        ContentAudit updateAudit = new ContentAudit();
        updateAudit.setAuditStatus(auditStatus);
        updateAudit.setAiConfidence(aiConfidence);
        updateAudit.setAuditReason(truncateForContentAuditAuditReason(auditReason));
        updateAudit.setUpdateTime(LocalDateTime.now());
        QueryWrapper<ContentAudit> updateWrapper = new QueryWrapper<>();
        updateWrapper.eq("id", contentAuditId);
        return contentAuditMapper.update(updateAudit, updateWrapper);
    }

    /**
     * AI 处理失败时按主键 id 更新审核记录
     * @return 影响行数（0 表示目标记录不存在或条件未命中）
     */
    private int updateAuditRecordOnFailure(Long contentAuditId, String failureReason) {
        ContentAudit updateAudit = new ContentAudit();
        updateAudit.setAuditStatus(AUDIT_STATUS_PENDING);
        updateAudit.setAiConfidence(new BigDecimal("0.0"));
        updateAudit.setAuditReason(truncateForContentAuditAuditReason(failureReason));
        updateAudit.setUpdateTime(LocalDateTime.now());
        QueryWrapper<ContentAudit> updateWrapper = new QueryWrapper<>();
        updateWrapper.eq("id", contentAuditId);
        return contentAuditMapper.update(updateAudit, updateWrapper);
    }

    /**
     * 根据审核结果更新书籍表
     */
    private void updateBookFromAudit(Long bookId, Integer auditStatus, String auditReason) {
        BookInfo updateBook = new BookInfo();
        updateBook.setId(bookId);
        updateBook.setAuditStatus(auditStatus);

        if (AUDIT_STATUS_REJECTED.equals(auditStatus)) {
            String shortReason = extractShortReason(auditReason, null);
            updateBook.setAuditReason(shortReason);
        } else {
            updateBook.setAuditReason(null);
        }

        updateBook.setUpdateTime(LocalDateTime.now());
        bookInfoMapper.updateById(updateBook);

        runAfterCommit(() -> {
            try {
                String cacheKey = CacheConsts.BOOK_INFO_HASH_PREFIX + bookId;
                stringRedisTemplate.delete(cacheKey);
                log.debug("已清除书籍信息缓存，bookId: {}", bookId);
            } catch (Exception e) {
                log.warn("清除书籍信息缓存失败，bookId: {}", bookId, e);
            }
        });
    }

    /**
     * 仅更新 book_info.audit_reason，不改 audit_status。用于 AI 返回 pending 时把原因回填给前端。
     */
    private void updateBookAuditReasonOnly(Long bookId, String auditReason) {
        BookInfo updateBook = new BookInfo();
        updateBook.setId(bookId);
        updateBook.setAuditReason(auditReason);
        updateBook.setUpdateTime(LocalDateTime.now());
        bookInfoMapper.updateById(updateBook);

        runAfterCommit(() -> {
            try {
                String cacheKey = CacheConsts.BOOK_INFO_HASH_PREFIX + bookId;
                stringRedisTemplate.delete(cacheKey);
            } catch (Exception e) {
                log.warn("清除书籍信息缓存失败，bookId: {}", bookId, e);
            }
        });
    }

    /**
     * 根据审核结果更新章节表
     */
    private void updateChapterFromAudit(Long chapterId, Integer auditStatus, String auditReason) {
        BookChapter updateChapter = new BookChapter();
        updateChapter.setId(chapterId);
        updateChapter.setAuditStatus(auditStatus);

        if (AUDIT_STATUS_REJECTED.equals(auditStatus)) {
            String shortReason = extractShortReason(auditReason, null);
            updateChapter.setAuditReason(shortReason);
        } else {
            updateChapter.setAuditReason(null);
        }

        updateChapter.setUpdateTime(LocalDateTime.now());
        bookChapterMapper.updateById(updateChapter);
    }

    /**
     * 更新bookInfo表的最新章节信息（当章节审核通过时调用）
     */
    private void updateBookInfoLastChapter(Long bookId) {
        if (bookId == null) {
            return;
        }
        
        // 查询当前该书真正的最新章节（只查询审核通过的章节，auditStatus=1）
        QueryWrapper<BookChapter> lastChapterQuery = new QueryWrapper<>();
        lastChapterQuery.eq(DatabaseConsts.BookChapterTable.COLUMN_BOOK_ID, bookId)
                .eq("audit_status", 1) // 只查询审核通过的章节
                .orderByDesc(DatabaseConsts.BookChapterTable.COLUMN_CHAPTER_NUM)
                .last("limit 1");
        BookChapter realLastChapter = bookChapterMapper.selectOne(lastChapterQuery);

        BookInfo updateBook = new BookInfo();
        updateBook.setId(bookId);
        
        if (realLastChapter != null) {
            updateBook.setLastChapterNum(realLastChapter.getChapterNum());
            updateBook.setLastChapterName(realLastChapter.getChapterName());
            updateBook.setLastChapterUpdateTime(realLastChapter.getUpdateTime());
        } else {
            // 如果没有审核通过的章节，清空最新章节信息
            updateBook.setLastChapterNum(null);
            updateBook.setLastChapterName(null);
            updateBook.setLastChapterUpdateTime(null);
        }

        updateBook.setUpdateTime(LocalDateTime.now());
        bookInfoMapper.updateById(updateBook);
    }

    /**
     * 发送书籍变更消息到ES
     */
    private void sendBookChangeMsg(Long bookId) {
        if (bookId == null) {
            return;
        }
        runAfterCommit(() -> {
            try {
                String destination = AmqpConsts.BookChangeMq.TOPIC + ":" + AmqpConsts.BookChangeMq.TAG_UPDATE;
                rocketMQTemplate.convertAndSend(destination, bookId);
                log.info("书籍变更 MQ 已投递（提交后），bookId: {}", bookId);
            } catch (Exception e) {
                log.error("发送书籍变更消息失败，书籍ID: {}", bookId, e);
            }
        });
    }

    private static String truncateForContentAuditAuditReason(String reason) {
        if (reason == null) {
            return null;
        }
        if (reason.length() <= CONTENT_AUDIT_AUDIT_REASON_MAX_LEN) {
            return reason;
        }
        int keep = CONTENT_AUDIT_AUDIT_REASON_MAX_LEN - 3;
        return keep > 0 ? reason.substring(0, keep) + "..." : "...";
    }

    @Override
    public String extractShortReason(String fullReason, BigDecimal aiConfidence) {
        if (fullReason == null || fullReason.trim().isEmpty()) {
            return "内容不符合平台规范，请检查后重新提交";
        }

        String shortReason = fullReason;
        if (shortReason.length() > 200) {
            shortReason = shortReason.substring(0, 200) + "...";
        }

        if (aiConfidence != null && aiConfidence.compareTo(CONFIDENCE_THRESHOLD) < 0) {
            shortReason += "（已进入人工审核流程）";
        }

        return shortReason;
    }

    /**
     * 发送审核通过消息给作者
     * @param bookId 书籍ID
     * @param contentName 内容名称（书籍名或章节名）
     * @param isBook 是否为书籍审核（true为书籍，false为章节）
     */
    private void sendAuditPassMessageToAuthor(Long bookId, String contentName, boolean isBook) {
        if (bookId == null) {
            return;
        }

        runAfterCommit(() -> {
            try {
                BookInfo bookInfo = bookInfoMapper.selectById(bookId);
                if (bookInfo == null || bookInfo.getAuthorId() == null) {
                    log.warn("无法获取书籍信息或作者ID，书籍ID: {}", bookId);
                    return;
                }

                String title = isBook ? "您的作品审核通过" : "您的章节审核通过";
                String content = String.format("恭喜！您的%s《%s》已通过审核，现已上线。",
                        isBook ? "作品" : "章节", contentName);
                String link = isBook ? "/author/book/list" : "/author/chapter/list?bookId=" + bookId;

                MessageSendReqDto messageDto = MessageSendReqDto.builder()
                        .receiverId(bookInfo.getAuthorId())
                        .receiverType(DatabaseConsts.MessageReceiveTable.RECEIVER_TYPE_AUTHOR)
                        .title(title)
                        .content(content)
                        .type(2)
                        .link(link)
                        .busId(bookId)
                        .busType(isBook ? "BOOK_AUDIT" : "CHAPTER_AUDIT")
                        .build();

                userFeignManager.sendMessage(messageDto);
                log.info("审核通过通知已发送（提交后），作者ID: {}, 书籍ID: {}", bookInfo.getAuthorId(), bookId);

                try {
                    String sseData = String.format(
                            "{\"title\":\"%s\",\"content\":\"%s\",\"link\":\"%s\",\"busId\":%d,\"busType\":\"%s\",\"timestamp\":%d}",
                            title, content, link, bookId, isBook ? "BOOK_AUDIT" : "CHAPTER_AUDIT", System.currentTimeMillis()
                    );
                    userFeignManager.pushNotificationToAuthor(bookInfo.getAuthorId(), "audit_pass", sseData);
                } catch (Exception e) {
                    log.warn("推送SSE实时通知失败，作者ID: {}", bookInfo.getAuthorId(), e);
                }
            } catch (Exception e) {
                log.error("发送审核通过消息给作者失败，书籍ID: {}", bookId, e);
            }
        });
    }

    /**
     * 仅当书籍审核通过且最新章节字段不为空时，增量加入布隆过滤器
     */
    private void addBookToBloomIfEligible(Long bookId) {
        if (bookId == null) {
            return;
        }
        try {
            BookInfo latestBookInfo = bookInfoMapper.selectById(bookId);
            if (latestBookInfo == null) {
                return;
            }
            boolean isAuditPassed = AUDIT_STATUS_PASSED.equals(latestBookInfo.getAuditStatus());
            boolean hasLastChapter = StringUtils.hasText(latestBookInfo.getLastChapterName());
            if (isAuditPassed && hasLastChapter) {
                bookExistBloomService.add(bookId);
                log.info("书籍满足布隆增量条件，已加入布隆过滤器，bookId={}", bookId);
            } else {
                log.debug("书籍暂不满足布隆增量条件，bookId={}, auditStatus={}, lastChapterName={}",
                        bookId, latestBookInfo.getAuditStatus(), latestBookInfo.getLastChapterName());
            }
        } catch (Exception e) {
            log.warn("书籍布隆增量写入失败，bookId={}", bookId, e);
        }
    }

    @Override
    public RestResp<List<com.novel.book.dto.resp.ContentAuditRespDto>> listNextAuditExperience(Long maxId) {
        QueryWrapper<ContentAudit> queryWrapper = new QueryWrapper<>();
        queryWrapper.in("audit_status", 1, 2)
                .eq("is_human_final", 1);
                
        if (maxId != null) {
            queryWrapper.gt("id", maxId);
        }
        
        queryWrapper.orderByAsc("id").last("LIMIT 100");
        
        List<ContentAudit> auditList = contentAuditMapper.selectList(queryWrapper);
        
        List<com.novel.book.dto.resp.ContentAuditRespDto> respDtoList = auditList.stream()
                .filter(audit -> audit.getContentText() != null && !audit.getContentText().trim().isEmpty())
                .map(audit -> {
            com.novel.book.dto.resp.ContentAuditRespDto dto = new com.novel.book.dto.resp.ContentAuditRespDto();
            dto.setId(audit.getId());
            dto.setSourceType(audit.getDataSource());
            dto.setSourceId(audit.getDataSourceId());
            dto.setContentText(audit.getContentText());
            dto.setAiConfidence(audit.getAiConfidence());
            dto.setAuditStatus(audit.getAuditStatus());
            dto.setIsHumanFinal(audit.getIsHumanFinal());
            dto.setAuditReason(audit.getAuditReason());
            dto.setViolationLabel(audit.getViolationLabel());
            dto.setKeySnippet(audit.getKeySnippet());
            dto.setAuditRule(audit.getAuditRule());
            dto.setCreateTime(audit.getCreateTime());
            dto.setUpdateTime(audit.getUpdateTime());
            return dto;
        }).collect(Collectors.toList());
        
        return RestResp.ok(respDtoList);
    }
}