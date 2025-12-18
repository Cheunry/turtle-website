package com.novel.book.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.novel.book.dao.entity.BookChapter;
import com.novel.book.dao.entity.BookInfo;
import com.novel.book.dao.entity.ContentAudit;
import com.novel.book.dao.mapper.BookChapterMapper;
import com.novel.book.dao.mapper.BookInfoMapper;
import com.novel.book.dao.mapper.ContentAuditMapper;
import com.novel.book.dto.req.BookAuditReqDto;
import com.novel.book.dto.req.ChapterAuditReqDto;
import com.novel.book.dto.resp.BookAuditRespDto;
import com.novel.book.dto.resp.ChapterAuditRespDto;
import com.novel.book.feign.AiFeignManager;
import com.novel.book.service.BookAuditService;
import com.novel.common.constant.AmqpConsts;
import com.novel.common.constant.DatabaseConsts;
import com.novel.common.constant.ErrorCodeEnum;
import com.novel.common.resp.RestResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

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
    private final AiFeignManager aiFeignManager;
    private final RocketMQTemplate rocketMQTemplate; // 添加MQ依赖

    /**
     * AI审核置信度阈值，低于此值需要人工审核
     */
    private static final BigDecimal CONFIDENCE_THRESHOLD = new BigDecimal("0.8");

    /**
     * 判断是否为新增小说的阈值（秒）：如果createTime和updateTime相差小于此值，认为是新增
     */
    private static final long NEW_BOOK_TIME_THRESHOLD = 60;

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
    public void auditBookInfo(BookInfo bookInfo) {
        // 1. 组装待审核内容
        BookAuditReqDto auditReq = BookAuditReqDto.builder()
                .id(bookInfo.getId())
                .bookName(bookInfo.getBookName())
                .bookDesc(bookInfo.getBookDesc())
                .build();

        // 2. 先查询是否已存在审核记录，如果存在则更新，不存在则插入
        ContentAudit initialAudit = null;
        ContentAudit existingAudit = null;
        
        try {
            QueryWrapper<ContentAudit> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("source_type", DATA_SOURCE_BOOK_INFO)
                    .eq("source_id", bookInfo.getId());
            existingAudit = contentAuditMapper.selectOne(queryWrapper);
        } catch (Exception e) {
            log.warn("查询审核记录失败，书籍ID: {}，将创建新记录。错误: {}", bookInfo.getId(), e.getMessage());
            // 查询失败（可能是字段不存在），当作没有记录处理
        }
        
        if (existingAudit != null) {
            // 如果已存在，更新现有记录
            initialAudit = existingAudit;
            initialAudit.setContentText(auditReq.getBookName() + " " + auditReq.getBookDesc());
            initialAudit.setAiConfidence(null); // 重置AI置信度
            initialAudit.setAuditStatus(AUDIT_STATUS_PENDING); // 重置为待审核
            initialAudit.setAuditReason(null); // 清空审核原因
            initialAudit.setUpdateTime(LocalDateTime.now()); // 更新时间
            try {
                // 使用联合主键更新
                QueryWrapper<ContentAudit> updateWrapper = new QueryWrapper<>();
                updateWrapper.eq("source_type", DATA_SOURCE_BOOK_INFO)
                        .eq("source_id", bookInfo.getId());
                contentAuditMapper.update(initialAudit, updateWrapper);
                log.info("书籍[{}]已更新审核表记录，等待AI审核", bookInfo.getId());
            } catch (Exception e) {
                log.error("更新审核记录失败，书籍ID: {}，将创建新记录。错误: {}", bookInfo.getId(), e.getMessage());
                // 更新失败，创建新记录
                initialAudit = createInitialAuditRecord(bookInfo, auditReq);
                try {
                    contentAuditMapper.insert(initialAudit);
                    log.info("书籍[{}]已写入审核表，等待AI审核", bookInfo.getId());
                } catch (Exception insertEx) {
                    log.error("插入审核记录失败，书籍ID: {}，错误: {}", bookInfo.getId(), insertEx.getMessage());
                    // 插入也失败，使用内存对象继续流程（不保存到数据库）
                    initialAudit = createInitialAuditRecord(bookInfo, auditReq);
                    initialAudit.setId(null); // 确保ID为null，避免后续更新时出错
                }
            }
        } else {
            // 如果不存在，创建新记录
            initialAudit = createInitialAuditRecord(bookInfo, auditReq);
            try {
                contentAuditMapper.insert(initialAudit);
                log.info("书籍[{}]已写入审核表，等待AI审核", bookInfo.getId());
            } catch (Exception e) {
                log.error("插入审核记录失败，书籍ID: {}，错误: {}，将继续执行AI审核", bookInfo.getId(), e.getMessage());
                // 插入失败，使用内存对象继续流程（不保存到数据库）
                initialAudit = createInitialAuditRecord(bookInfo, auditReq);
                initialAudit.setId(null); // 确保ID为null，避免后续更新时出错
            }
        }

        // 3. 调用AI服务审核
        RestResp<BookAuditRespDto> aiResp;
        try {
            aiResp = aiFeignManager.auditBook(auditReq);
        } catch (Exception e) {
            log.error("AI审核服务调用异常，书籍ID: {}, 错误: {}", bookInfo.getId(), e.getMessage(), e);
            // AI审核服务调用失败，更新审核表记录失败原因（如果记录存在）
            if (initialAudit != null && initialAudit.getDataSource() != null && initialAudit.getDataSourceId() != null) {
                try {
                    updateAuditRecordOnFailure(initialAudit.getDataSource(), initialAudit.getDataSourceId(), 
                            "AI审核服务调用失败: " + e.getMessage());
                } catch (Exception updateEx) {
                    log.error("更新审核记录失败，书籍ID: {}", bookInfo.getId(), updateEx);
                }
            }
            return;
        }
        
        if (!aiResp.isOk()) {
            log.error("AI审核失败，书籍ID: {}, 错误码: {}, 错误信息: {}", 
                    bookInfo.getId(), aiResp.getCode(), aiResp.getMessage());
            // AI审核失败，更新审核表记录失败原因（如果记录存在）
            if (initialAudit != null && initialAudit.getDataSource() != null && initialAudit.getDataSourceId() != null) {
                try {
                    updateAuditRecordOnFailure(initialAudit.getDataSource(), initialAudit.getDataSourceId(), 
                            "AI审核失败: " + (aiResp.getMessage() != null ? aiResp.getMessage() : "未知错误"));
                } catch (Exception updateEx) {
                    log.error("更新审核记录失败，书籍ID: {}", bookInfo.getId(), updateEx);
                }
            }
            return;
        }
        
        if (aiResp.getData() == null) {
            log.error("AI审核返回数据为空，书籍ID: {}", bookInfo.getId());
            // AI审核返回数据为空，更新审核表记录失败原因（如果记录存在）
            if (initialAudit != null && initialAudit.getDataSource() != null && initialAudit.getDataSourceId() != null) {
                try {
                    updateAuditRecordOnFailure(initialAudit.getDataSource(), initialAudit.getDataSourceId(), 
                            "AI审核返回数据为空");
                } catch (Exception updateEx) {
                    log.error("更新审核记录失败，书籍ID: {}", bookInfo.getId(), updateEx);
                }
            }
            return;
        }

        BookAuditRespDto aiResult = aiResp.getData();
        Integer auditStatus = aiResult.getAuditStatus();
        BigDecimal aiConfidence = aiResult.getAiConfidence();
        String fullReason = aiResult.getAuditReason();

        // 4. 判断审核结果类型
        boolean isPassed = AUDIT_STATUS_PASSED.equals(auditStatus);
        boolean isHighConfidence = aiConfidence != null &&
                aiConfidence.compareTo(CONFIDENCE_THRESHOLD) >= 0;

        // 5. 更新审核表和书籍表
        if (isPassed && isHighConfidence) {
            // 情况1：AI审核通过且置信度高 -> 更新审核表和书籍表
            if (initialAudit != null && initialAudit.getDataSource() != null && initialAudit.getDataSourceId() != null) {
                try {
                    updateAuditRecord(initialAudit.getDataSource(), initialAudit.getDataSourceId(), 
                            AUDIT_STATUS_PASSED, aiResult);
                    log.debug("书籍[{}]审核表已更新", bookInfo.getId());
                } catch (Exception e) {
                    log.error("更新审核表失败，书籍ID: {}", bookInfo.getId(), e);
                }
            }
            
            try {
                updateBookDirectly(bookInfo.getId(), AUDIT_STATUS_PASSED, null);
                log.debug("书籍[{}]书籍表已更新", bookInfo.getId());
            } catch (Exception e) {
                log.error("更新书籍表失败，书籍ID: {}", bookInfo.getId(), e);
            }
            
            log.info("书籍[{}]AI审核通过，置信度: {}，已更新审核表和书籍表", bookInfo.getId(), aiConfidence);
            
            // 审核通过后发送ES消息（新增和更新都发送）
            try {
                sendBookChangeMsg(bookInfo.getId());
                log.info("书籍[{}]审核通过，已发送ES更新消息", bookInfo.getId());
            } catch (Exception e) {
                log.error("发送ES消息失败，书籍ID: {}", bookInfo.getId(), e);
                // ES消息发送失败不影响审核结果
            }

        } else if (isPassed && !isHighConfidence) {
            // 情况2：AI审核通过但置信度低 -> 更新审核表为待人工审核，书籍表保持待审核状态
            if (initialAudit != null && initialAudit.getDataSource() != null && initialAudit.getDataSourceId() != null) {
                try {
                    updateAuditRecord(initialAudit.getDataSource(), initialAudit.getDataSourceId(), 
                            AUDIT_STATUS_PENDING, aiResult);
                    log.info("书籍[{}]AI审核通过但置信度低[{}]，已更新审核表等待人工审核",
                            bookInfo.getId(), aiConfidence);
                } catch (Exception e) {
                    log.error("更新审核表失败，书籍ID: {}", bookInfo.getId(), e);
                }
            }

        } else {
            // 情况3：AI审核不通过 -> 更新审核表和书籍表为不通过
            String shortReason = extractShortReason(fullReason, aiConfidence);
            if (initialAudit != null && initialAudit.getDataSource() != null && initialAudit.getDataSourceId() != null) {
                try {
                    updateAuditRecord(initialAudit.getDataSource(), initialAudit.getDataSourceId(), 
                            AUDIT_STATUS_REJECTED, aiResult);
                    log.debug("书籍[{}]审核表已更新为不通过", bookInfo.getId());
                } catch (Exception e) {
                    log.error("更新审核表失败，书籍ID: {}", bookInfo.getId(), e);
                }
            }
            
            try {
                updateBookDirectly(bookInfo.getId(), AUDIT_STATUS_REJECTED, shortReason);
                log.debug("书籍[{}]书籍表已更新为不通过", bookInfo.getId());
            } catch (Exception e) {
                log.error("更新书籍表失败，书籍ID: {}", bookInfo.getId(), e);
            }
            
            log.warn("书籍[{}]AI审核不通过，原因: {}，已更新审核表和书籍表", bookInfo.getId(), shortReason);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void auditChapter(BookChapter bookChapter) {
        // 1. 组装待审核内容
        ChapterAuditReqDto auditReq = ChapterAuditReqDto.builder()
                .bookId(bookChapter.getBookId())
                .chapterNum(bookChapter.getChapterNum())
                .chapterName(bookChapter.getChapterName())
                .content(bookChapter.getContent())
                .build();

        // 2. 先查询是否已存在审核记录，如果存在则更新，不存在则插入
        ContentAudit initialAudit = null;
        ContentAudit existingAudit = null;
        
        try {
            QueryWrapper<ContentAudit> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("source_type", DATA_SOURCE_BOOK_CHAPTER)
                    .eq("source_id", bookChapter.getId());
            existingAudit = contentAuditMapper.selectOne(queryWrapper);
        } catch (Exception e) {
            log.warn("查询审核记录失败，章节ID: {}，将创建新记录。错误: {}", bookChapter.getId(), e.getMessage());
            // 查询失败（可能是字段不存在），当作没有记录处理
        }
        
        if (existingAudit != null) {
            // 如果已存在，更新现有记录
            initialAudit = existingAudit;
            initialAudit.setContentText(auditReq.getChapterName() + " " + auditReq.getContent());
            initialAudit.setAiConfidence(null); // 重置AI置信度
            initialAudit.setAuditStatus(AUDIT_STATUS_PENDING); // 重置为待审核
            initialAudit.setAuditReason(null); // 清空审核原因
            initialAudit.setUpdateTime(LocalDateTime.now()); // 更新时间
            try {
                // 使用联合主键更新
                QueryWrapper<ContentAudit> updateWrapper = new QueryWrapper<>();
                updateWrapper.eq("source_type", DATA_SOURCE_BOOK_CHAPTER)
                        .eq("source_id", bookChapter.getId());
                contentAuditMapper.update(initialAudit, updateWrapper);
                log.info("章节[{}]已更新审核表记录，等待AI审核", bookChapter.getId());
            } catch (Exception e) {
                log.error("更新审核记录失败，章节ID: {}，将创建新记录。错误: {}", bookChapter.getId(), e.getMessage());
                // 更新失败，创建新记录
                initialAudit = createInitialChapterAuditRecord(bookChapter, auditReq);
                try {
                    contentAuditMapper.insert(initialAudit);
                    log.info("章节[{}]已写入审核表，等待AI审核", bookChapter.getId());
                } catch (Exception insertEx) {
                    log.error("插入审核记录失败，章节ID: {}，错误: {}", bookChapter.getId(), insertEx.getMessage());
                    // 插入也失败，使用内存对象继续流程（不保存到数据库）
                    initialAudit = createInitialChapterAuditRecord(bookChapter, auditReq);
                    initialAudit.setId(null); // 确保ID为null，避免后续更新时出错
                }
            }
        } else {
            // 如果不存在，创建新记录
            initialAudit = createInitialChapterAuditRecord(bookChapter, auditReq);
            try {
                contentAuditMapper.insert(initialAudit);
                log.info("章节[{}]已写入审核表，等待AI审核", bookChapter.getId());
            } catch (Exception e) {
                log.error("插入审核记录失败，章节ID: {}，错误: {}，将继续执行AI审核", bookChapter.getId(), e.getMessage());
                // 插入失败，使用内存对象继续流程（不保存到数据库）
                initialAudit = createInitialChapterAuditRecord(bookChapter, auditReq);
                initialAudit.setId(null); // 确保ID为null，避免后续更新时出错
            }
        }

        // 3. 调用AI服务审核
        RestResp<ChapterAuditRespDto> aiResp;
        try {
            aiResp = aiFeignManager.auditChapter(auditReq);
        } catch (Exception e) {
            log.error("AI审核服务调用异常，章节ID: {}, 错误: {}", bookChapter.getId(), e.getMessage(), e);
            // AI审核服务调用失败，更新审核表记录失败原因（如果记录存在）
            if (initialAudit != null && initialAudit.getDataSource() != null && initialAudit.getDataSourceId() != null) {
                try {
                    updateAuditRecordOnFailure(initialAudit.getDataSource(), initialAudit.getDataSourceId(), 
                            "AI审核服务调用失败: " + e.getMessage());
                } catch (Exception updateEx) {
                    log.error("更新审核记录失败，章节ID: {}", bookChapter.getId(), updateEx);
                }
            }
            return;
        }
        
        if (!aiResp.isOk()) {
            log.error("AI审核失败，章节ID: {}, 错误码: {}, 错误信息: {}", 
                    bookChapter.getId(), aiResp.getCode(), aiResp.getMessage());
            // AI审核失败，更新审核表记录失败原因（如果记录存在）
            if (initialAudit != null && initialAudit.getDataSource() != null && initialAudit.getDataSourceId() != null) {
                try {
                    updateAuditRecordOnFailure(initialAudit.getDataSource(), initialAudit.getDataSourceId(), 
                            "AI审核失败: " + (aiResp.getMessage() != null ? aiResp.getMessage() : "未知错误"));
                } catch (Exception updateEx) {
                    log.error("更新审核记录失败，章节ID: {}", bookChapter.getId(), updateEx);
                }
            }
            return;
        }
        
        if (aiResp.getData() == null) {
            log.error("AI审核返回数据为空，章节ID: {}", bookChapter.getId());
            // AI审核返回数据为空，更新审核表记录失败原因（如果记录存在）
            if (initialAudit != null && initialAudit.getDataSource() != null && initialAudit.getDataSourceId() != null) {
                try {
                    updateAuditRecordOnFailure(initialAudit.getDataSource(), initialAudit.getDataSourceId(), 
                            "AI审核返回数据为空");
                } catch (Exception updateEx) {
                    log.error("更新审核记录失败，章节ID: {}", bookChapter.getId(), updateEx);
                }
            }
            return;
        }

        ChapterAuditRespDto aiResult = aiResp.getData();
        Integer auditStatus = aiResult.getAuditStatus();
        BigDecimal aiConfidence = aiResult.getAiConfidence();
        String fullReason = aiResult.getAuditReason();

        log.info("章节[{}]收到AI审核结果，auditStatus: {}, aiConfidence: {}, reason: {}", 
                bookChapter.getId(), auditStatus, aiConfidence, 
                fullReason != null && fullReason.length() > 100 ? fullReason.substring(0, 100) + "..." : fullReason);

        // 4. 判断审核结果类型
        boolean isPassed = AUDIT_STATUS_PASSED.equals(auditStatus);
        boolean isHighConfidence = aiConfidence != null &&
                aiConfidence.compareTo(CONFIDENCE_THRESHOLD) >= 0;

        // 5. 更新审核表和章节表
        if (isPassed && isHighConfidence) {
            // 情况1：AI审核通过且置信度高 -> 更新审核表和章节表
            if (initialAudit != null && initialAudit.getDataSource() != null && initialAudit.getDataSourceId() != null) {
                try {
                    updateChapterAuditRecord(initialAudit.getDataSource(), initialAudit.getDataSourceId(), 
                            AUDIT_STATUS_PASSED, aiResult);
                    log.debug("章节[{}]审核表已更新", bookChapter.getId());
                } catch (Exception e) {
                    log.error("更新审核表失败，章节ID: {}", bookChapter.getId(), e);
                }
            }
            
            try {
                updateChapterDirectly(bookChapter.getId(), AUDIT_STATUS_PASSED, null);
                log.debug("章节[{}]章节表已更新", bookChapter.getId());
            } catch (Exception e) {
                log.error("更新章节表失败，章节ID: {}", bookChapter.getId(), e);
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
                log.info("章节[{}]审核通过，已发送ES更新消息", bookChapter.getId());
            } catch (Exception e) {
                log.error("发送ES消息失败，书籍ID: {}", bookChapter.getBookId(), e);
                // ES消息发送失败不影响审核结果
            }
            
            log.info("章节[{}]AI审核通过，置信度: {}，已更新审核表和章节表", bookChapter.getId(), aiConfidence);

        } else if (isPassed && !isHighConfidence) {
            // 情况2：AI审核通过但置信度低 -> 更新审核表为待人工审核，章节表保持待审核状态
            if (initialAudit != null && initialAudit.getDataSource() != null && initialAudit.getDataSourceId() != null) {
                try {
                    updateChapterAuditRecord(initialAudit.getDataSource(), initialAudit.getDataSourceId(), 
                            AUDIT_STATUS_PENDING, aiResult);
                    log.info("章节[{}]AI审核通过但置信度低[{}]，已更新审核表等待人工审核",
                            bookChapter.getId(), aiConfidence);
                } catch (Exception e) {
                    log.error("更新审核表失败，章节ID: {}", bookChapter.getId(), e);
                }
            }

        } else {
            // 情况3：AI审核不通过 -> 更新审核表和章节表为不通过
            String shortReason = extractShortReason(fullReason, aiConfidence);
            if (initialAudit != null && initialAudit.getDataSource() != null && initialAudit.getDataSourceId() != null) {
                try {
                    updateChapterAuditRecord(initialAudit.getDataSource(), initialAudit.getDataSourceId(), 
                            AUDIT_STATUS_REJECTED, aiResult);
                    log.debug("章节[{}]审核表已更新为不通过", bookChapter.getId());
                } catch (Exception e) {
                    log.error("更新审核表失败，章节ID: {}", bookChapter.getId(), e);
                }
            }
            
            try {
                log.info("开始更新章节表，章节ID: {}, auditStatus: {}, reason: {}", 
                        bookChapter.getId(), AUDIT_STATUS_REJECTED, shortReason);
                updateChapterDirectly(bookChapter.getId(), AUDIT_STATUS_REJECTED, shortReason);
                log.info("章节[{}]章节表已更新为不通过，auditStatus: {}", bookChapter.getId(), AUDIT_STATUS_REJECTED);
            } catch (Exception e) {
                log.error("更新章节表失败，章节ID: {}, 错误信息: {}", bookChapter.getId(), e.getMessage(), e);
            }
            
            log.warn("章节[{}]AI审核不通过，原因: {}，已更新审核表和章节表", bookChapter.getId(), shortReason);
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
                }
            }
        }

        log.info("人工审核完成，审核记录ID: {}，审核结果: {}", auditId,
                AUDIT_STATUS_PASSED.equals(auditStatus) ? "通过" : "不通过");

        return RestResp.ok();
    }

    /**
     * 直接更新书籍表
     */
    private void updateBookDirectly(Long bookId, Integer auditStatus, String rejectReason) {
        BookInfo updateBook = new BookInfo();
        updateBook.setId(bookId);
        updateBook.setAuditStatus(auditStatus);
        updateBook.setAuditReason(rejectReason);
        updateBook.setUpdateTime(LocalDateTime.now());
        bookInfoMapper.updateById(updateBook);
    }

    /**
     * 直接更新章节表
     */
    private void updateChapterDirectly(Long chapterId, Integer auditStatus, String rejectReason) {
        log.info("updateChapterDirectly 开始执行，chapterId: {}, auditStatus: {}, rejectReason: {}", 
                chapterId, auditStatus, rejectReason);
        BookChapter updateChapter = new BookChapter();
        updateChapter.setId(chapterId);
        updateChapter.setAuditStatus(auditStatus);
        updateChapter.setAuditReason(rejectReason);
        updateChapter.setUpdateTime(LocalDateTime.now());
        int updateCount = bookChapterMapper.updateById(updateChapter);
        log.info("updateChapterDirectly 执行完成，chapterId: {}, 更新行数: {}", chapterId, updateCount);
        if (updateCount == 0) {
            log.warn("警告：章节ID {} 更新行数为0，可能章节不存在或已被删除", chapterId);
        }
    }

    /**
     * 创建初始审核记录（待审核状态）
     */
    private ContentAudit createInitialAuditRecord(BookInfo bookInfo, BookAuditReqDto auditReq) {
        String contentText = auditReq.getBookName() + " " + auditReq.getBookDesc();

        return ContentAudit.builder()
                .dataSource(DATA_SOURCE_BOOK_INFO)
                .dataSourceId(bookInfo.getId())
                .contentText(contentText)
                .aiConfidence(null) // 初始时还没有AI审核结果
                .auditStatus(AUDIT_STATUS_PENDING) // 待审核状态
                .auditReason(null) // 初始时还没有审核原因
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
    }

    /**
     * 创建初始章节审核记录（待审核状态）
     */
    private ContentAudit createInitialChapterAuditRecord(BookChapter bookChapter, ChapterAuditReqDto auditReq) {
        String contentText = auditReq.getChapterName() + " " + auditReq.getContent();

        return ContentAudit.builder()
                .dataSource(DATA_SOURCE_BOOK_CHAPTER)
                .dataSourceId(bookChapter.getId())
                .contentText(contentText)
                .aiConfidence(null) // 初始时还没有AI审核结果
                .auditStatus(AUDIT_STATUS_PENDING) // 待审核状态
                .auditReason(null) // 初始时还没有审核原因
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
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
     * 保存到审核表（保留此方法，可能其他地方会用到）
     */
    private void saveToAuditTable(BookInfo bookInfo, BookAuditReqDto auditReq,
                                  BookAuditRespDto aiResult, Integer auditStatus) {
        String contentText = auditReq.getBookName() + " " + auditReq.getBookDesc();

        ContentAudit contentAudit = ContentAudit.builder()
                .dataSource(DATA_SOURCE_BOOK_INFO)
                .dataSourceId(bookInfo.getId())
                .contentText(contentText)
                .aiConfidence(aiResult.getAiConfidence())
                .auditStatus(auditStatus)
                .auditReason(aiResult.getAuditReason())
                .createTime(LocalDateTime.now())
                .build();

        contentAuditMapper.insert(contentAudit);
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
     * 判断是否为新增小说
     * 通过比较createTime和updateTime来判断
     */
    private boolean isNewBook(BookInfo bookInfo) {
        if (bookInfo.getCreateTime() == null || bookInfo.getUpdateTime() == null) {
            return false;
        }
        
        // 如果createTime和updateTime相差小于阈值，认为是新增
        long seconds = ChronoUnit.SECONDS.between(bookInfo.getCreateTime(), bookInfo.getUpdateTime());
        return seconds <= NEW_BOOK_TIME_THRESHOLD;
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
}