package com.novel.book.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.novel.book.dao.entity.BookChapter;
import com.novel.book.dao.entity.BookInfo;
import com.novel.book.dao.mapper.BookChapterMapper;
import com.novel.book.dao.mapper.BookInfoMapper;
import com.novel.book.dto.resp.BookChapterRespDto;
import com.novel.book.dto.resp.BookContentAboutRespDto;
import com.novel.book.service.BookReadService;
import com.novel.common.constant.DatabaseConsts;
import com.novel.common.resp.RestResp;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BookReadServiceImpl implements BookReadService {

    private final BookChapterMapper bookChapterMapper;
    private final BookInfoMapper bookInfoMapper;

    /**
     * 看小说某章节内容
     * @param bookId,chapterNum 章节ID
     * @return 该章节小说内容
     */
    @Override
    public RestResp<BookContentAboutRespDto> getBookContentAbout(Long bookId, Integer chapterNum) {

        // 1. 查询小说基本信息
        BookInfo bookInfo = bookInfoMapper.selectById(bookId);
        
        // 检查书籍审核状态：只有审核通过的书籍才能被读者查看
        if (bookInfo == null || bookInfo.getAuditStatus() == null || bookInfo.getAuditStatus() != 1) {
            // 书籍不存在或未审核通过，返回空内容
            return RestResp.ok(BookContentAboutRespDto.builder()
                .bookInfo(BookContentAboutRespDto.BookInfo.builder()
                    .categoryName(null)
                    .authorName(null)
                    .build())
                .chapterInfo(BookContentAboutRespDto.ChapterInfo.builder()
                    .bookId(null)
                    .chapterNum(null)
                    .chapterName(null)
                    .chapterWordCount(null)
                    .chapterUpdateTime(null)
                    .build())
                .bookContent(null)
                .build());
        }

        // 2. 查询章节信息（包含 content），只查询审核通过的章节
        QueryWrapper<BookChapter> chapterQueryWrapper = new QueryWrapper<>();
        chapterQueryWrapper.eq(DatabaseConsts.BookChapterTable.COLUMN_BOOK_ID, bookId)
                .eq(DatabaseConsts.BookChapterTable.COLUMN_CHAPTER_NUM, chapterNum)
                .eq("audit_status", 1); // 只查询审核通过的章节
        BookChapter bookChapter = bookChapterMapper.selectOne(chapterQueryWrapper);

        // 3. 组装并返回
        return RestResp.ok(BookContentAboutRespDto.builder()
            .bookInfo(BookContentAboutRespDto.BookInfo.builder()
                .categoryName(bookInfo != null ? bookInfo.getCategoryName() : null)
                .authorName(bookInfo != null ? bookInfo.getAuthorName() : null)
                .build())
            .chapterInfo(BookContentAboutRespDto.ChapterInfo.builder()
                .bookId(bookChapter != null ? bookChapter.getBookId() : null)
                .chapterNum(bookChapter != null ? bookChapter.getChapterNum() : null)
                .chapterName(bookChapter != null ? bookChapter.getChapterName() : null)
                .chapterWordCount(bookChapter != null ? bookChapter.getWordCount() : null)
                .chapterUpdateTime(bookChapter != null ? bookChapter.getUpdateTime() : null)
                .build())
            .bookContent(bookChapter != null ? bookChapter.getContent() : null)
            .build());
    }

    /**
     * 看书籍下一章
     * @param bookId 书籍ID
     * @param chapterNum 章节号
     * @return 下一章章节号
     */
    @Override
    public RestResp<Integer> getNextChapterNum(Long bookId, Integer chapterNum) {

        // 获取下一章（只查询审核通过的章节）
        QueryWrapper<BookChapter> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(DatabaseConsts.BookChapterTable.COLUMN_BOOK_ID, bookId)
                .eq("audit_status", 1) // 只查询审核通过的章节
                .gt(DatabaseConsts.BookChapterTable.COLUMN_CHAPTER_NUM, chapterNum)
                .orderByAsc(DatabaseConsts.BookChapterTable.COLUMN_CHAPTER_NUM)
                .last(DatabaseConsts.SqlEnum.LIMIT_1.getSql());
        return RestResp.ok(
                Optional.ofNullable(bookChapterMapper.selectOne(queryWrapper))
                        .map(BookChapter::getChapterNum).orElse(null)
        );

    }

    /**
     * 看书籍上一章
     * @param bookId 书籍ID
     * @param chapterNum 章节号
     * @return 上一章章节号
     */
    @Override
    public RestResp<Integer> getPreChapterNum(Long bookId, Integer chapterNum) {

        // 获取上一章
        QueryWrapper<BookChapter> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(DatabaseConsts.BookChapterTable.COLUMN_BOOK_ID, bookId)
                .lt(DatabaseConsts.BookChapterTable.COLUMN_CHAPTER_NUM, chapterNum)
                .orderByDesc(DatabaseConsts.BookChapterTable.COLUMN_CHAPTER_NUM)
                .last(DatabaseConsts.SqlEnum.LIMIT_1.getSql());
        return RestResp.ok(
                Optional.ofNullable(bookChapterMapper.selectOne(queryWrapper))
                        .map(BookChapter::getChapterNum).orElse(null)
        );
    }

    /**
     * 获取书籍目录
     * @param bookId 书籍ID
     * @return 书籍章节目录列表
     */
    @Override
    public RestResp<List<BookChapterRespDto>> getBookChapter(Long bookId) {

        // 只查询审核通过的章节（auditStatus=1）
        QueryWrapper<BookChapter> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(DatabaseConsts.BookChapterTable.COLUMN_BOOK_ID, bookId)
                .eq("audit_status", 1) // 只查询审核通过的章节
                .orderByAsc(DatabaseConsts.BookChapterTable.COLUMN_CHAPTER_NUM);

        return RestResp.ok(
                bookChapterMapper.selectList(queryWrapper).stream()
                        .map(v -> BookChapterRespDto.builder()
                                .chapterNum(v.getChapterNum())
                                .chapterName(v.getChapterName())
                                .chapterWordCount(v.getWordCount())
                                .chapterUpdateTime(v.getUpdateTime())
                                .isVip(v.getIsVip()).build()
                        ).toList()
        );

    }


}
