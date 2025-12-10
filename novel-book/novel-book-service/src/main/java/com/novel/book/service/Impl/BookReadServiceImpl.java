package com.novel.book.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.novel.book.dao.entity.BookChapter;
import com.novel.book.dao.mapper.BookChapterMapper;
import com.novel.book.dao.mapper.BookInfoMapper;
import com.novel.book.dto.resp.BookChapterRespDto;
import com.novel.book.dto.resp.BookContentAboutRespDto;
import com.novel.book.dto.resp.BookInfoRespDto;
import com.novel.book.manager.cache.BookChapterCacheManager;
import com.novel.book.manager.cache.BookContentCacheManager;
import com.novel.book.manager.cache.BookInfoCacheManager;
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

    private final BookChapterCacheManager bookChapterCacheManager;
    private final BookContentCacheManager bookContentCacheManager;
    private final BookInfoCacheManager bookInfoCacheManager;
    private final BookChapterMapper bookChapterMapper;


    /**
     * 看小说某章节内容
     * @param bookId,chapterNum 章节ID
     * @return 该章节小说内容
     */
    @Override
    public RestResp<BookContentAboutRespDto> getBookContentAbout(Long bookId, Integer chapterNum) {

        BookChapterRespDto bookChapter = bookChapterCacheManager.getChapter(bookId, chapterNum);
        String bookContent = bookContentCacheManager.getBookContent(bookId, chapterNum);
        BookInfoRespDto bookInfo = bookInfoCacheManager.getBookInfo(bookChapter.getBookId());

        return RestResp.ok(
                BookContentAboutRespDto.builder()
                        .bookInfo(bookInfo)
                        .chapterInfo(bookChapter)
                        .bookContent(bookContent)
                        .build()                    // 小说信息 章节信息 章节内容
        );

    }
    /**
     * 看下一章
     * 接口：next_chapter_id/{chapterId} ，返回下一章的id即可
     */
    @Override
    public RestResp<Integer> getNextChapterId(Long bookId, Integer chapterNum) {

        // 获取下一章
        QueryWrapper<BookChapter> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(DatabaseConsts.BookChapterTable.COLUMN_BOOK_ID, bookId)
                .gt(DatabaseConsts.BookChapterTable.COLUMN_CHAPTER_NUM, chapterNum)
                .orderByAsc(DatabaseConsts.BookChapterTable.COLUMN_CHAPTER_NUM)
                .last(DatabaseConsts.SqlEnum.LIMIT_1.getSql());
        return RestResp.ok(
                Optional.ofNullable(bookChapterMapper.selectOne(queryWrapper))
                        .map(BookChapter::getChapterNum).orElse(null)
        );

    }

    /**
     * 看上一章
     * pre_chapter_id/{chapterId}
     */
    @Override
    public RestResp<Integer> getPreChapterId(Long bookId, Integer chapterNum) {

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
     * 看目录
     * http://localhost:1024/#/chapter_list/1357668191920263169
     * chapter/list
     */
    @Override
    public RestResp<List<BookChapterRespDto>> getBookChapter(Long bookId) {

        QueryWrapper<BookChapter> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(DatabaseConsts.BookChapterTable.COLUMN_BOOK_ID, bookId)
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
