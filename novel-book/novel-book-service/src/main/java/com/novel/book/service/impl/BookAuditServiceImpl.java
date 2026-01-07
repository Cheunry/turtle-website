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

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
                QueryWrapper<ContentAudit> queryWrapper = new QueryWrapper<>();
                queryWrapper.eq("source_type", DATA_SOURCE_BOOK_CHAPTER)
                        .eq("source_id", chapterId);
                ContentAudit existingAudit = contentAuditMapper.selectOne(queryWrapper);
                
                if (existingAudit != null) {
                    updateAuditRecordOnFailure(DATA_SOURCE_BOOK_CHAPTER, chapterId, 
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
            QueryWrapper<ContentAudit> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("source_type", DATA_SOURCE_BOOK_CHAPTER)
                    .eq("source_id", chapterId);
            initialAudit = contentAuditMapper.selectOne(queryWrapper);
        } catch (Exception e) {
            log.warn("查询审核记录失败，章节ID: {}，将创建新记录。错误: {}", chapterId, e.getMessage());
        }

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
                contentAuditMapper.insert(initialAudit);
                log.debug("章节[{}]审核记录已创建", chapterId);
            } catch (Exception e) {
                log.error("创建审核记录失败，章节ID: {}", chapterId, e);
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
        boolean isPassed = AUDIT_STATUS_PASSED.equals(auditStatus);
        boolean isHighConfidence = aiConfidence != null &&
                aiConfidence.compareTo(CONFIDENCE_THRESHOLD) >= 0;

        // 6. 更新审核表和章节表
        if (isPassed && isHighConfidence) {
            // 情况1：AI审核通过且置信度高 -> 更新审核表和章节表
            if (initialAudit != null && initialAudit.getDataSource() != null && initialAudit.getDataSourceId() != null) {
                try {
                    updateChapterAuditRecord(initialAudit.getDataSource(), initialAudit.getDataSourceId(), 
                            AUDIT_STATUS_PASSED, aiResult);
                    log.debug("章节[{}]审核表已更新", chapterId);
                } catch (Exception e) {
                    log.error("更新审核表失败，章节ID: {}", chapterId, e);
                }
            }
            
            try {
                updateChapterDirectly(chapterId, AUDIT_STATUS_PASSED, null);
                log.debug("章节[{}]章节表已更新", chapterId);
            } catch (Exception e) {
                log.error("更新章节表失败，章节ID: {}", chapterId, e);
            }
            
            // 审核通过后，更新bookInfo表的最新章节信息
            try {
                updateBookInfoLastChapter(bookChapter.getBookId());
                log.debug("书籍[{}]最新章节信息已更新", bookChapter.getBookId());
            } catch (Exception e) {
                log.error("更新书籍最新章节信息失败，书籍ID: {}", bookChapter.getBookId(), e);
            }
            
            // 审核通过后发送ES消息
            try {
                sendBookChangeMsg(bookChapter.getBookId());
                log.info("章节[{}]审核通过，已发送ES更新消息", chapterId);
            } catch (Exception e) {
                log.error("发送ES消息失败，书籍ID: {}", bookChapter.getBookId(), e);
            }
            
            // 审核通过后发送消息给作者
            try {
                BookInfo bookInfo = bookInfoMapper.selectById(bookChapter.getBookId());
                if (bookInfo != null) {
                    sendAuditPassMessageToAuthor(bookChapter.getBookId(), 
                            bookInfo.getBookName() + " - " + bookChapter.getChapterName(), false);
                    log.info("章节[{}]审核通过，已发送消息给作者", chapterId);
                }
            } catch (Exception e) {
                log.error("发送消息给作者失败，章节ID: {}", chapterId, e);
            }
            
            log.info("章节[{}]AI审核通过，置信度: {}，已更新审核表和章节表", chapterId, aiConfidence);

        } else if (isPassed && !isHighConfidence) {
            // 情况2：AI审核通过但置信度低 -> 更新审核表为待人工审核，章节表保持待审核状态
            if (initialAudit != null && initialAudit.getDataSource() != null && initialAudit.getDataSourceId() != null) {
                try {
                    updateChapterAuditRecord(initialAudit.getDataSource(), initialAudit.getDataSourceId(), 
                            AUDIT_STATUS_PENDING, aiResult);
                    log.info("章节[{}]AI审核通过但置信度低[{}]，已更新审核表等待人工审核",
                            chapterId, aiConfidence);
                } catch (Exception e) {
                    log.error("更新审核表失败，章节ID: {}", chapterId, e);
                }
            }

        } else {
            // 情况3：AI审核不通过 -> 更新审核表和章节表为不通过
            String shortReason = extractShortReason(fullReason, aiConfidence);
            if (initialAudit != null && initialAudit.getDataSource() != null && initialAudit.getDataSourceId() != null) {
                try {
                    updateChapterAuditRecord(initialAudit.getDataSource(), initialAudit.getDataSourceId(), 
                            AUDIT_STATUS_REJECTED, aiResult);
                    log.debug("章节[{}]审核表已更新为不通过", chapterId);
                } catch (Exception e) {
                    log.error("更新审核表失败，章节ID: {}", chapterId, e);
                }
            }
            
            try {
                log.info("开始更新章节表，章节ID: {}, auditStatus: {}, reason: {}", 
                        chapterId, AUDIT_STATUS_REJECTED, shortReason);
                updateChapterDirectly(chapterId, AUDIT_STATUS_REJECTED, shortReason);
                log.info("章节[{}]章节表已更新为不通过，auditStatus: {}", chapterId, AUDIT_STATUS_REJECTED);
            } catch (Exception e) {
                log.error("更新章节表失败，章节ID: {}, 错误信息: {}", chapterId, e.getMessage(), e);
            }
            
            log.warn("章节[{}]AI审核不通过，原因: {}，已更新审核表和章节表", chapterId, shortReason);
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
                QueryWrapper<ContentAudit> queryWrapper = new QueryWrapper<>();
                queryWrapper.eq("source_type", DATA_SOURCE_BOOK_INFO)
                        .eq("source_id", bookId);
                ContentAudit existingAudit = contentAuditMapper.selectOne(queryWrapper);
                
                if (existingAudit != null) {
                    updateAuditRecordOnFailure(DATA_SOURCE_BOOK_INFO, bookId, 
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
            QueryWrapper<ContentAudit> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("source_type", DATA_SOURCE_BOOK_INFO)
                    .eq("source_id", bookId);
            initialAudit = contentAuditMapper.selectOne(queryWrapper);
        } catch (Exception e) {
            log.warn("查询审核记录失败，书籍ID: {}，将创建新记录。错误: {}", bookId, e.getMessage());
        }

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
                contentAuditMapper.insert(initialAudit);
                log.debug("书籍[{}]审核记录已创建", bookId);
            } catch (Exception e) {
                log.error("创建审核记录失败，书籍ID: {}", bookId, e);
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
        boolean isPassed = AUDIT_STATUS_PASSED.equals(auditStatus);
        boolean isHighConfidence = aiConfidence != null &&
                aiConfidence.compareTo(CONFIDENCE_THRESHOLD) >= 0;

        // 6. 更新审核表和书籍表
        if (isPassed && isHighConfidence) {
            // 情况1：AI审核通过且置信度高 -> 更新审核表和书籍表
            if (initialAudit != null && initialAudit.getDataSource() != null && initialAudit.getDataSourceId() != null) {
                try {
                    updateAuditRecord(initialAudit.getDataSource(), initialAudit.getDataSourceId(), 
                            AUDIT_STATUS_PASSED, aiResult);
                    log.debug("书籍[{}]审核表已更新", bookId);
                } catch (Exception e) {
                    log.error("更新审核表失败，书籍ID: {}", bookId, e);
                }
            }
            
            try {
                updateBookFromAudit(bookId, AUDIT_STATUS_PASSED, null);
                log.debug("书籍[{}]书籍表已更新", bookId);
            } catch (Exception e) {
                log.error("更新书籍表失败，书籍ID: {}", bookId, e);
            }
            
            log.info("书籍[{}]AI审核通过，置信度: {}，已更新审核表和书籍表", bookId, aiConfidence);
            
            // 审核通过后发送ES消息（新增和更新都发送）
            try {
                sendBookChangeMsg(bookId);
                log.info("书籍[{}]审核通过，已发送ES更新消息", bookId);
            } catch (Exception e) {
                log.error("发送ES消息失败，书籍ID: {}", bookId, e);
                // ES消息发送失败不影响审核结果
            }
            
            // 审核通过后发送消息给作者
            try {
                sendAuditPassMessageToAuthor(bookId, bookInfo.getBookName(), true);
                log.info("书籍[{}]审核通过，已发送消息给作者", bookId);
            } catch (Exception e) {
                log.error("发送消息给作者失败，书籍ID: {}", bookId, e);
                // 消息发送失败不影响审核结果
            }

        } else if (isPassed && !isHighConfidence) {
            // 情况2：AI审核通过但置信度低 -> 更新审核表为待人工审核，书籍表保持待审核状态
            if (initialAudit != null && initialAudit.getDataSource() != null && initialAudit.getDataSourceId() != null) {
                try {
                    updateAuditRecord(initialAudit.getDataSource(), initialAudit.getDataSourceId(), 
                            AUDIT_STATUS_PENDING, aiResult);
                    log.info("书籍[{}]AI审核通过但置信度低[{}]，已更新审核表等待人工审核",
                            bookId, aiConfidence);
                } catch (Exception e) {
                    log.error("更新审核表失败，书籍ID: {}", bookId, e);
                }
            }

        } else {
            // 情况3：AI审核不通过 -> 更新审核表和书籍表为不通过
            String shortReason = extractShortReason(fullReason, aiConfidence);
            if (initialAudit != null && initialAudit.getDataSource() != null && initialAudit.getDataSourceId() != null) {
                try {
                    updateAuditRecord(initialAudit.getDataSource(), initialAudit.getDataSourceId(), 
                            AUDIT_STATUS_REJECTED, aiResult);
                    log.debug("书籍[{}]审核表已更新为不通过", bookId);
                } catch (Exception e) {
                    log.error("更新审核表失败，书籍ID: {}", bookId, e);
                }
            }
            
            try {
                updateBookFromAudit(bookId, AUDIT_STATUS_REJECTED, shortReason);
                log.debug("书籍[{}]书籍表已更新为不通过", bookId);
            } catch (Exception e) {
                log.error("更新书籍表失败，书籍ID: {}", bookId, e);
            }
            
            log.warn("书籍[{}]AI审核不通过，原因: {}，已更新审核表和书籍表", bookId, shortReason);
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

        // 3. 更新审核表（使用联合主键更新）
        audit.setAuditStatus(auditStatus);
        audit.setAuditReason(auditReason);
        audit.setUpdateTime(LocalDateTime.now()); // 使用 update_time 而不是 audit_time
        
        QueryWrapper<ContentAudit> updateWrapper = new QueryWrapper<>();
        updateWrapper.eq("source_type", audit.getDataSource())
                .eq("source_id", audit.getDataSourceId());
        contentAuditMapper.update(audit, updateWrapper);

        // 4. 根据数据来源同步更新对应的业务表
        if (DATA_SOURCE_BOOK_INFO.equals(audit.getDataSource())) {
            // 更新书籍表
            updateBookFromAudit(audit.getDataSourceId(), auditStatus, auditReason);
            
            // 如果审核通过，发送ES消息（新增和更新都发送）
            if (AUDIT_STATUS_PASSED.equals(auditStatus)) {
                try {
                    sendBookChangeMsg(audit.getDataSourceId());
                    log.info("人工审核通过，书籍[{}]已发送ES更新消息", audit.getDataSourceId());
                } catch (Exception e) {
                    log.error("发送ES消息失败，书籍ID: {}", audit.getDataSourceId(), e);
                    // ES消息发送失败不影响审核结果
                }
                
                // 审核通过后发送消息给作者
                try {
                    BookInfo bookInfo = bookInfoMapper.selectById(audit.getDataSourceId());
                    if (bookInfo != null) {
                        sendAuditPassMessageToAuthor(bookInfo.getId(), bookInfo.getBookName(), true);
                        log.info("人工审核通过，书籍[{}]已发送消息给作者", audit.getDataSourceId());
                    }
                } catch (Exception e) {
                    log.error("发送消息给作者失败，书籍ID: {}", audit.getDataSourceId(), e);
                    // 消息发送失败不影响审核结果
                }
            }
        } else if (DATA_SOURCE_BOOK_CHAPTER.equals(audit.getDataSource())) {
            // 更新章节表
            updateChapterFromAudit(audit.getDataSourceId(), auditStatus, auditReason);
            
            // 如果审核通过，更新bookInfo表的最新章节信息并发送ES消息
            if (AUDIT_STATUS_PASSED.equals(auditStatus)) {
                // 查询章节信息获取bookId
                BookChapter chapter = bookChapterMapper.selectById(audit.getDataSourceId());
                if (chapter != null) {
                    try {
                        updateBookInfoLastChapter(chapter.getBookId());
                        log.info("人工审核通过，章节[{}]已更新书籍最新章节信息", audit.getDataSourceId());
                    } catch (Exception e) {
                        log.error("更新书籍最新章节信息失败，章节ID: {}", audit.getDataSourceId(), e);
                    }
                    
                    try {
                        sendBookChangeMsg(chapter.getBookId());
                        log.info("人工审核通过，章节[{}]已发送ES更新消息", audit.getDataSourceId());
                    } catch (Exception e) {
                        log.error("发送ES消息失败，章节ID: {}", audit.getDataSourceId(), e);
                        // ES消息发送失败不影响审核结果
                    }
                    
                    // 审核通过后发送消息给作者
                    try {
                        BookInfo bookInfo = bookInfoMapper.selectById(chapter.getBookId());
                        if (bookInfo != null) {
                            sendAuditPassMessageToAuthor(chapter.getBookId(), 
                                    bookInfo.getBookName() + " - " + chapter.getChapterName(), false);
                            log.info("人工审核通过，章节[{}]已发送消息给作者", audit.getDataSourceId());
                        }
                    } catch (Exception e) {
                        log.error("发送消息给作者失败，章节ID: {}", audit.getDataSourceId(), e);
                        // 消息发送失败不影响审核结果
                    }
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
     * 更新审核记录（根据AI审核结果）
     * 使用 source_type 和 source_id 作为条件更新（联合主键）
     */
    private void updateAuditRecord(Integer sourceType, Long sourceId, Integer auditStatus, BookAuditRespDto aiResult) {
        ContentAudit updateAudit = new ContentAudit();
        updateAudit.setAuditStatus(auditStatus);
        updateAudit.setAiConfidence(aiResult.getAiConfidence());
        updateAudit.setAuditReason(aiResult.getAuditReason());
        updateAudit.setUpdateTime(LocalDateTime.now()); // 更新时间
        
        // 使用联合主键更新
        QueryWrapper<ContentAudit> updateWrapper = new QueryWrapper<>();
        updateWrapper.eq("source_type", sourceType)
                .eq("source_id", sourceId);
        contentAuditMapper.update(updateAudit, updateWrapper);
    }

    /**
     * 更新章节审核记录（根据AI审核结果）
     * 使用 source_type 和 source_id 作为条件更新（联合主键）
     */
    private void updateChapterAuditRecord(Integer sourceType, Long sourceId, Integer auditStatus, ChapterAuditRespDto aiResult) {
        ContentAudit updateAudit = new ContentAudit();
        updateAudit.setAuditStatus(auditStatus);
        updateAudit.setAiConfidence(aiResult.getAiConfidence());
        updateAudit.setAuditReason(aiResult.getAuditReason());
        updateAudit.setUpdateTime(LocalDateTime.now()); // 更新时间
        
        // 使用联合主键更新
        QueryWrapper<ContentAudit> updateWrapper = new QueryWrapper<>();
        updateWrapper.eq("source_type", sourceType)
                .eq("source_id", sourceId);
        contentAuditMapper.update(updateAudit, updateWrapper);
    }

    /**
     * 更新审核记录（AI审核失败时）
     * 使用 source_type 和 source_id 作为条件更新（联合主键）
     */
    private void updateAuditRecordOnFailure(Integer sourceType, Long sourceId, String failureReason) {
        ContentAudit updateAudit = new ContentAudit();
        updateAudit.setAuditStatus(AUDIT_STATUS_PENDING); // 保持待审核状态
        updateAudit.setAiConfidence(new BigDecimal("0.0")); // 置信度为0
        updateAudit.setAuditReason(failureReason); // 记录失败原因
        updateAudit.setUpdateTime(LocalDateTime.now()); // 更新时间
        
        // 使用联合主键更新
        QueryWrapper<ContentAudit> updateWrapper = new QueryWrapper<>();
        updateWrapper.eq("source_type", sourceType)
                .eq("source_id", sourceId);
        contentAuditMapper.update(updateAudit, updateWrapper);
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
        
        // 清除 Redis 缓存，确保下次查询时获取最新数据（包括审核状态）
        try {
            String cacheKey = CacheConsts.BOOK_INFO_HASH_PREFIX + bookId;
            stringRedisTemplate.delete(cacheKey);
            log.debug("已清除书籍信息缓存，bookId: {}, cacheKey: {}", bookId, cacheKey);
        } catch (Exception e) {
            log.warn("清除书籍信息缓存失败，bookId: {}, 不影响业务", bookId, e);
        }
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
        try {
            // 构建 Destination: Topic:Tag
            String destination = AmqpConsts.BookChangeMq.TOPIC + ":" + AmqpConsts.BookChangeMq.TAG_UPDATE;
            // 发送消息，消息体就是 bookId
            rocketMQTemplate.convertAndSend(destination, bookId);
            log.debug("已发送书籍变更消息，书籍ID: {}", bookId);
        } catch (Exception e) {
            log.error("发送书籍变更消息失败，书籍ID: {}", bookId, e);
        }
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
        
        try {
            // 查询书籍信息获取作者ID
            BookInfo bookInfo = bookInfoMapper.selectById(bookId);
            if (bookInfo == null || bookInfo.getAuthorId() == null) {
                log.warn("无法获取书籍信息或作者ID，书籍ID: {}", bookId);
                return;
            }
            
            // 构建消息内容
            String title = isBook ? "您的作品审核通过" : "您的章节审核通过";
            String content = String.format("恭喜！您的%s《%s》已通过审核，现已上线。", 
                    isBook ? "作品" : "章节", contentName);
            String link = isBook ? "/author/book/list" : "/author/chapter/list?bookId=" + bookId;
            
            // 构建消息发送DTO
            MessageSendReqDto messageDto = MessageSendReqDto.builder()
                    .receiverId(bookInfo.getAuthorId())
                    .receiverType(DatabaseConsts.MessageReceiveTable.RECEIVER_TYPE_AUTHOR) // 1表示作者
                    .title(title)
                    .content(content)
                    .type(2) // 2表示作家助手/审核消息
                    .link(link)
                    .busId(bookId)
                    .busType(isBook ? "BOOK_AUDIT" : "CHAPTER_AUDIT")
                    .build();
            
            // 发送消息到数据库
            userFeignManager.sendMessage(messageDto);
            log.debug("已发送审核通过消息给作者，作者ID: {}, 书籍ID: {}", bookInfo.getAuthorId(), bookId);
            
            // 通过SSE推送实时通知
            try {
                // 构建SSE推送的JSON数据
                String sseData = String.format(
                        "{\"title\":\"%s\",\"content\":\"%s\",\"link\":\"%s\",\"busId\":%d,\"busType\":\"%s\",\"timestamp\":%d}",
                        title, content, link, bookId, isBook ? "BOOK_AUDIT" : "CHAPTER_AUDIT", System.currentTimeMillis()
                );
                userFeignManager.pushNotificationToAuthor(bookInfo.getAuthorId(), "audit_pass", sseData);
            } catch (Exception e) {
                log.warn("推送SSE实时通知失败，但不影响消息保存，作者ID: {}", bookInfo.getAuthorId(), e);
            }
        } catch (Exception e) {
            log.error("发送审核通过消息给作者失败，书籍ID: {}", bookId, e);
            // 不抛出异常，避免影响审核流程
        }
    }
}