package com.novel.book.mq;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.novel.book.dao.entity.BookChapter;
import com.novel.book.dao.entity.BookInfo;
import com.novel.book.dao.mapper.BookChapterMapper;
import com.novel.book.dao.mapper.BookInfoMapper;
import com.novel.book.dto.mq.BookInfoUpdateMqDto;
import com.novel.common.constant.AmqpConsts;
import com.novel.common.constant.DatabaseConsts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 书籍信息更新MQ消费者（异步更新书籍字数和最新章节信息）
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
    topic = AmqpConsts.BookInfoUpdateMq.TOPIC,
    selectorExpression = AmqpConsts.BookInfoUpdateMq.TAG_UPDATE_INFO,
    consumerGroup = AmqpConsts.BookInfoUpdateMq.CONSUMER_GROUP_UPDATE_INFO
)
public class BookInfoUpdateListener implements RocketMQListener<BookInfoUpdateMqDto> {

    private final BookInfoMapper bookInfoMapper;
    private final BookChapterMapper bookChapterMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onMessage(BookInfoUpdateMqDto updateDto) {
        Long bookId = updateDto.getBookId();
        log.debug("收到书籍信息更新MQ消息，bookId: {}, chapterId: {}, isNew: {}", 
                bookId, updateDto.getChapterId(), updateDto.getIsNew());
        
        try {
            // 1. 查询书籍信息
            BookInfo bookInfo = bookInfoMapper.selectById(bookId);
            if (bookInfo == null) {
                log.warn("书籍不存在，忽略更新，bookId: {}", bookId);
                return;
            }

            BookInfo updateBook = new BookInfo();
            updateBook.setId(bookId);

            // 2. 计算新总字数
            int currentTotal = bookInfo.getWordCount() == null ? 0 : bookInfo.getWordCount();
            int newChapterWordCount = updateDto.getNewChapterWordCount() != null ? 
                    updateDto.getNewChapterWordCount() : 0;
            
            if (Boolean.TRUE.equals(updateDto.getIsNew())) {
                // 新增章节，增加字数
                updateBook.setWordCount(currentTotal + newChapterWordCount);
            } else {
                // 更新章节，计算字数差
                int oldChapterWordCount = updateDto.getOldChapterWordCount() != null ? 
                        updateDto.getOldChapterWordCount() : 0;
                updateBook.setWordCount(currentTotal - oldChapterWordCount + newChapterWordCount);
            }

            // 3. 查询当前该书真正的最新章节（只查询审核通过的章节，auditStatus=1）
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
        } catch (Exception e) {
            log.error("处理书籍信息更新MQ消息失败，bookId: {}, chapterId: {}", 
                    bookId, updateDto.getChapterId(), e);
            // 不抛出异常，避免MQ消息重复消费
        }
    }
}

