package com.novel.book.mq;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.novel.book.dao.entity.BookChapter;
import com.novel.book.dao.entity.BookInfo;
import com.novel.book.dao.mapper.BookChapterMapper;
import com.novel.book.dao.mapper.BookInfoMapper;
import com.novel.book.dto.req.BookDelReqDto;
import com.novel.book.dto.req.ChapterDelReqDto;
import com.novel.common.constant.DatabaseConsts;
import com.novel.common.constant.ErrorCodeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 与 RocketMQ 事务消息的 {@code executeLocalTransaction} 对齐的本地数据库操作。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookLocalTxExecutor {

    private final BookInfoMapper bookInfoMapper;
    private final BookChapterMapper bookChapterMapper;

    /**
     * 删除章节并更新书籍汇总信息（与事务半消息同事务：成功则投递 ES 同步消息）。
     */
    public void deleteBookChapter(ChapterDelReqDto dto) {
        BookInfo bookInfo = bookInfoMapper.selectById(dto.getBookId());
        if (bookInfo == null) {
            throw new BookLocalTxAbortException(ErrorCodeEnum.USER_UN_AUTH);
        }
        if (!Objects.equals(bookInfo.getAuthorId(), dto.getAuthorId())) {
            throw new BookLocalTxAbortException(ErrorCodeEnum.USER_UN_AUTH);
        }

        QueryWrapper<BookChapter> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(DatabaseConsts.BookChapterTable.COLUMN_BOOK_ID, dto.getBookId())
                .eq(DatabaseConsts.BookChapterTable.COLUMN_CHAPTER_NUM, dto.getChapterNum());
        BookChapter bookChapter = bookChapterMapper.selectOne(queryWrapper);
        if (bookChapter == null) {
            throw new BookLocalTxAbortException(ErrorCodeEnum.USER_REQUEST_PARAM_ERROR, "章节不存在");
        }

        int count = bookChapter.getWordCount() == null ? 0 : bookChapter.getWordCount();
        BookInfo book = bookInfoMapper.selectById(dto.getBookId());

        if (Objects.nonNull(book)) {
            int currentWords = book.getWordCount() == null ? 0 : book.getWordCount();
            book.setWordCount(currentWords - count);

            if (book.getWordCount() > 0 && book.getLastChapterNum() != null
                    && book.getLastChapterNum().equals(bookChapter.getChapterNum())) {
                QueryWrapper<BookChapter> bookChapterQueryWrapper = new QueryWrapper<>();
                bookChapterQueryWrapper.eq(DatabaseConsts.BookChapterTable.COLUMN_BOOK_ID, book.getId())
                        .eq("audit_status", 1)
                        .ne(DatabaseConsts.BookChapterTable.COLUMN_CHAPTER_NUM, dto.getChapterNum())
                        .orderByDesc(DatabaseConsts.BookChapterTable.COLUMN_CHAPTER_UPDATE_TIME)
                        .last("limit 1");
                BookChapter bookChapter1 = bookChapterMapper.selectOne(bookChapterQueryWrapper);
                if (bookChapter1 != null) {
                    book.setLastChapterNum(bookChapter1.getChapterNum());
                    book.setLastChapterName(bookChapter1.getChapterName());
                    book.setLastChapterUpdateTime(bookChapter1.getUpdateTime());
                } else {
                    book.setLastChapterNum(null);
                    book.setLastChapterName(null);
                    book.setLastChapterUpdateTime(null);
                }
            } else if (book.getWordCount() <= 0 && book.getLastChapterNum() != null
                    && book.getLastChapterNum().equals(bookChapter.getChapterNum())) {
                book.setWordCount(0);
                book.setLastChapterNum(null);
                book.setLastChapterName(null);
                book.setLastChapterUpdateTime(null);
            }
            bookInfoMapper.updateById(book);
        }

        bookChapterMapper.delete(queryWrapper);
    }

    /**
     * 删除书籍及其章节（与事务半消息同事务）。
     */
    public void deleteBook(BookDelReqDto dto) {
        QueryWrapper<BookChapter> chapterWrapper = new QueryWrapper<>();
        chapterWrapper.eq(DatabaseConsts.BookChapterTable.COLUMN_BOOK_ID, dto.getBookId());
        bookChapterMapper.delete(chapterWrapper);
        bookInfoMapper.deleteById(dto.getBookId());
    }
}
