package com.novel.book.mq;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.novel.book.dao.entity.BookChapter;
import com.novel.book.dao.entity.BookInfo;
import com.novel.book.dao.entity.ContentAudit;
import com.novel.book.dao.mapper.BookChapterMapper;
import com.novel.book.dao.mapper.BookInfoMapper;
import com.novel.book.dao.mapper.ContentAuditMapper;
import com.novel.book.dto.mq.ChapterAuditRequestMqDto;
import com.novel.book.dto.mq.ChapterSubmitMqDto;
import com.novel.common.constant.AmqpConsts;
import com.novel.common.constant.DatabaseConsts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 章节提交MQ消费者（处理章节更新/创建的所有数据库操作）
 * 包含：章节入库、书籍字数和最新章节更新、触发审核、发送变更通知
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
    topic = AmqpConsts.ChapterSubmitMq.TOPIC,
    selectorExpression = AmqpConsts.ChapterSubmitMq.TAG_SUBMIT,
    consumerGroup = AmqpConsts.ChapterSubmitMq.CONSUMER_GROUP_SUBMIT
)
public class ChapterSubmitListener implements RocketMQListener<ChapterSubmitMqDto> {

    /** 与 content_audit.source_type 一致：章节 */
    private static final int CONTENT_AUDIT_SOURCE_CHAPTER = 1;
    private static final int CONTENT_AUDIT_STATUS_PENDING = 0;

    private final BookInfoMapper bookInfoMapper;
    private final BookChapterMapper bookChapterMapper;
    private final ContentAuditMapper contentAuditMapper;
    private final BookRocketMqTxPublisher bookRocketMqTxPublisher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onMessage(ChapterSubmitMqDto submitDto) {
        log.info("收到章节提交MQ消息，bookId: {}, operationType: {}, chapterNum: {}", 
                submitDto.getBookId(), submitDto.getOperationType(), submitDto.getChapterNum());
        
        try {
            // 1. 权限校验（双重校验，确保安全）
            BookInfo bookInfo = bookInfoMapper.selectById(submitDto.getBookId());
            if (bookInfo == null) {
                log.warn("书籍不存在，忽略处理，bookId: {}", submitDto.getBookId());
                return;
            }
            if (!Objects.equals(bookInfo.getAuthorId(), submitDto.getAuthorId())) {
                log.warn("权限校验失败，书籍不属于该作者，bookId: {}, authorId: {}, submitAuthorId: {}", 
                        submitDto.getBookId(), bookInfo.getAuthorId(), submitDto.getAuthorId());
                return;
            }

            if ("UPDATE".equals(submitDto.getOperationType())) {
                // 更新章节逻辑
                handleChapterUpdate(submitDto, bookInfo);
            } else if ("CREATE".equals(submitDto.getOperationType())) {
                // 创建章节逻辑
                handleChapterCreate(submitDto, bookInfo);
            } else {
                log.warn("未知的操作类型，忽略处理，operationType: {}", submitDto.getOperationType());
            }
        } catch (Exception e) {
            log.error("处理章节提交MQ消息失败，bookId: {}, operationType: {}", 
                    submitDto.getBookId(), submitDto.getOperationType(), e);
            // 不抛出异常，避免MQ消息重复消费
        }
    }

    /**
     * 处理章节更新
     */
    private void handleChapterUpdate(ChapterSubmitMqDto submitDto, BookInfo bookInfo) {
        // 1. 查询章节
        QueryWrapper<BookChapter> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(DatabaseConsts.BookChapterTable.COLUMN_BOOK_ID, submitDto.getBookId())
                    .eq(DatabaseConsts.BookChapterTable.COLUMN_CHAPTER_NUM, submitDto.getOldChapterNum());
        BookChapter chapter = bookChapterMapper.selectOne(queryWrapper);

        if (chapter == null) {
            log.warn("章节不存在，忽略更新，bookId: {}, oldChapterNum: {}", 
                    submitDto.getBookId(), submitDto.getOldChapterNum());
            return;
        }
        
        // 2. 记录旧字数
        int oldWordCount = chapter.getWordCount() == null ? 0 : chapter.getWordCount();

        // 3. 校验章节号是否变更且已存在
        if (!Objects.equals(chapter.getChapterNum(), submitDto.getChapterNum())) {
            QueryWrapper<BookChapter> checkWrapper = new QueryWrapper<>();
            checkWrapper.eq(DatabaseConsts.BookChapterTable.COLUMN_BOOK_ID, submitDto.getBookId())
                    .eq(DatabaseConsts.BookChapterTable.COLUMN_CHAPTER_NUM, submitDto.getChapterNum());
            if (bookChapterMapper.selectCount(checkWrapper) > 0) {
                log.warn("章节号已存在，忽略更新，bookId: {}, chapterNum: {}", 
                        submitDto.getBookId(), submitDto.getChapterNum());
                return;
            }
        }
        
        // 4. 更新章节信息
        chapter.setChapterNum(submitDto.getChapterNum());
        chapter.setChapterName(submitDto.getChapterName());
        chapter.setContent(submitDto.getContent());
        chapter.setIsVip(submitDto.getIsVip());
        chapter.setUpdateTime(LocalDateTime.now());
        int newWordCount = submitDto.getContent() != null ? submitDto.getContent().length() : 0;
        chapter.setWordCount(newWordCount);
        
        // 如果开启审核，重置审核状态为待审核
        if (Boolean.TRUE.equals(submitDto.getAuditEnable())) {
            chapter.setAuditStatus(0);
            chapter.setAuditReason(null);
        }

        bookChapterMapper.updateById(chapter);
        log.debug("章节更新完成，chapterId: {}, bookId: {}", chapter.getId(), submitDto.getBookId());

        // 5. 更新书籍字数和最新章节信息（原 BookInfoUpdateListener 逻辑）
        updateBookInfo(bookInfo, oldWordCount, newWordCount, false);

        // 6. 如果开启审核，写入待审快照并发审核请求 MQ（异步处理）
        if (Boolean.TRUE.equals(submitDto.getAuditEnable())) {
            insertPendingChapterContentAudit(chapter);
            sendAuditRequest(chapter, bookInfo);
        }

        // 7. 仅在不走审核或关闭审核时通知订阅用户（审核通过后的通知在 BookAuditServiceImpl 中发送）
        if (!Boolean.TRUE.equals(submitDto.getAuditEnable())) {
            sendBookChapterUpdateNotice(bookInfo, chapter);
        }
    }

    /**
     * 处理章节创建
     */
    private void handleChapterCreate(ChapterSubmitMqDto submitDto, BookInfo bookInfo) {
        // 1. 校验章节号是否重复
        QueryWrapper<BookChapter> checkWrapper = new QueryWrapper<>();
        checkWrapper.eq(DatabaseConsts.BookChapterTable.COLUMN_BOOK_ID, submitDto.getBookId())
                    .eq(DatabaseConsts.BookChapterTable.COLUMN_CHAPTER_NUM, submitDto.getChapterNum());
        
        if (bookChapterMapper.selectCount(checkWrapper) > 0) {
            log.warn("章节号已存在，忽略创建，bookId: {}, chapterNum: {}", 
                    submitDto.getBookId(), submitDto.getChapterNum());
            return;
        }

        // 2. 创建章节实体
        BookChapter chapter = new BookChapter();
        chapter.setBookId(submitDto.getBookId());
        chapter.setChapterNum(submitDto.getChapterNum());
        chapter.setChapterName(submitDto.getChapterName());
        chapter.setContent(submitDto.getContent());
        int newWordCount = submitDto.getContent() != null ? submitDto.getContent().length() : 0;
        chapter.setWordCount(newWordCount);
        chapter.setIsVip(submitDto.getIsVip());
        chapter.setAuditStatus(Boolean.TRUE.equals(submitDto.getAuditEnable()) ? 0 : 1);
        chapter.setCreateTime(LocalDateTime.now());
        chapter.setUpdateTime(LocalDateTime.now());

        bookChapterMapper.insert(chapter);
        log.debug("章节创建完成，chapterId: {}, bookId: {}", chapter.getId(), submitDto.getBookId());

        // 3. 更新书籍字数和最新章节信息（原 BookInfoUpdateListener 逻辑）
        updateBookInfo(bookInfo, 0, newWordCount, true);

        // 4. 如果开启审核，写入待审快照并发审核请求 MQ（异步处理）
        if (Boolean.TRUE.equals(submitDto.getAuditEnable())) {
            insertPendingChapterContentAudit(chapter);
            sendAuditRequest(chapter, bookInfo);
        }

        // 5. 仅在不走审核或关闭审核时通知订阅用户（审核通过后的通知在 BookAuditServiceImpl 中发送）
        if (!Boolean.TRUE.equals(submitDto.getAuditEnable())) {
            sendBookChapterUpdateNotice(bookInfo, chapter);
        }
    }

    /**
     * 更新书籍字数和最新章节信息
     */
    private void updateBookInfo(BookInfo bookInfo, int oldChapterWordCount, int newChapterWordCount, boolean isNewChapter) {
        Long bookId = bookInfo.getId();
        BookInfo updateBook = new BookInfo();
        updateBook.setId(bookId);

        // 1. 计算新总字数
        int currentTotal = bookInfo.getWordCount() == null ? 0 : bookInfo.getWordCount();
        if (isNewChapter) {
            updateBook.setWordCount(currentTotal + newChapterWordCount);
        } else {
            updateBook.setWordCount(currentTotal - oldChapterWordCount + newChapterWordCount);
        }

        // 2. 查询当前该书真正的最新章节（只查询审核通过的章节，auditStatus=1）
        QueryWrapper<BookChapter> lastChapterQuery = new QueryWrapper<>();
        lastChapterQuery.eq(DatabaseConsts.BookChapterTable.COLUMN_BOOK_ID, bookId)
                .eq("audit_status", 1) // 只查询审核通过的章节
                .orderByDesc(DatabaseConsts.BookChapterTable.COLUMN_CHAPTER_NUM)
                .last("limit 1");
        BookChapter realLastChapter = bookChapterMapper.selectOne(lastChapterQuery);

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
        log.debug("书籍信息更新完成，bookId: {}, 新字数: {}", bookId, updateBook.getWordCount());

        if (bookInfo.getAuditStatus() != null && bookInfo.getAuditStatus() == 1) {
            String destination = AmqpConsts.BookChangeMq.TOPIC + ":" + AmqpConsts.BookChangeMq.TAG_UPDATE;
            bookRocketMqTxPublisher.sendAfterLocalDbCommit(destination, bookId);
            log.debug("MQ 已注册事务投递（提交后）: ES同步 bookId={}", bookId);
        }
    }

    /**
     * 每次提交章节审核时插入一条待审核记录，便于后台列表立即看到本次送审；AI 回调按 id 最新一条更新。
     */
    private void insertPendingChapterContentAudit(BookChapter chapter) {
        if (chapter == null || chapter.getId() == null) {
            return;
        }
        String text = chapter.getChapterName() + " " + (chapter.getContent() != null ? chapter.getContent() : "");
        ContentAudit row = ContentAudit.builder()
                .dataSource(CONTENT_AUDIT_SOURCE_CHAPTER)
                .dataSourceId(chapter.getId())
                .contentText(text)
                .auditStatus(CONTENT_AUDIT_STATUS_PENDING)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        try {
            contentAuditMapper.insert(row);
            log.debug("章节[{}]已插入待审核 content_audit，id: {}", chapter.getId(), row.getId());
        } catch (Exception e) {
            log.error("插入章节待审核 content_audit 失败，chapterId: {}", chapter.getId(), e);
        }
    }

    /**
     * 发送审核请求MQ
     */
    private void sendAuditRequest(BookChapter chapter, BookInfo bookInfo) {
        String taskId = generateTaskId(chapter.getId());
        ChapterAuditRequestMqDto auditRequest = ChapterAuditRequestMqDto.builder()
                .taskId(taskId)
                .chapterId(chapter.getId())
                .bookId(chapter.getBookId())
                .chapterNum(chapter.getChapterNum())
                .chapterName(chapter.getChapterName())
                .content(chapter.getContent())
                .categoryId(bookInfo != null ? bookInfo.getCategoryId() : null)
                .categoryName(bookInfo != null ? bookInfo.getCategoryName() : null)
                .authorId(bookInfo != null ? bookInfo.getAuthorId() : null)
                .build();
        String destination = AmqpConsts.BookAuditRequestMq.TOPIC + ":"
                + AmqpConsts.BookAuditRequestMq.TAG_AUDIT_CHAPTER_REQUEST;
        bookRocketMqTxPublisher.sendAfterLocalDbCommit(destination, auditRequest);
        log.debug("章节审核请求已注册事务投递 chapterId={} taskId={}", chapter.getId(), taskId);
    }

    /**
     * 发送书籍章节更新通知消息
     */
    private void sendBookChapterUpdateNotice(BookInfo bookInfo, BookChapter chapter) {
        if (bookInfo == null) {
            return;
        }
        com.novel.book.dto.mq.BookChapterUpdateDto updateDto =
                com.novel.book.dto.mq.BookChapterUpdateDto.builder()
                        .bookId(bookInfo.getId())
                        .bookName(bookInfo.getBookName())
                        .authorId(bookInfo.getAuthorId())
                        .authorName(bookInfo.getAuthorName())
                        .chapterId(chapter.getId())
                        .chapterName(chapter.getChapterName())
                        .chapterNum(chapter.getChapterNum())
                        .updateTime(LocalDateTime.now())
                        .build();
        String destination = AmqpConsts.BookChangeMq.TOPIC + ":" + AmqpConsts.BookChangeMq.TAG_CHAPTER_UPDATE;
        bookRocketMqTxPublisher.sendAfterLocalDbCommit(destination, updateDto);
        log.debug("章节更新通知已注册事务投递 chapterId={}", chapter.getId());
    }

    /**
     * 生成任务ID（用于关联审核请求和结果，保证幂等性）
     */
    private String generateTaskId(Long chapterId) {
        return String.format("audit_chapter_%s_%s_%s", 
                chapterId, 
                System.currentTimeMillis(), 
                java.util.UUID.randomUUID().toString().substring(0, 8));
    }
}
