package com.novel.book.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.novel.book.dao.entity.BookChapter;
import com.novel.book.dao.entity.BookContent;
import com.novel.book.dao.entity.BookInfo;
import com.novel.book.dao.mapper.BookChapterMapper;
import com.novel.book.dao.mapper.BookContentMapper;
import com.novel.book.dto.resp.BookChapterRespDto;
import com.novel.book.dto.resp.BookContentAboutRespDto;
import com.novel.book.dto.resp.BookInfoRespDto;
import com.novel.book.manager.cache.BookChapterCacheManager;
import com.novel.book.manager.cache.BookContentCacheManager;
import com.novel.book.manager.cache.BookInfoCacheManager;
import com.novel.book.service.BookReadService;
import com.novel.common.auth.UserHolder;
import com.novel.common.constant.DatabaseConsts;
import com.novel.common.resp.RestResp;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookReadServiceImpl implements BookReadService {

    private final BookChapterCacheManager bookChapterCacheManager;
    private final BookContentCacheManager bookContentCacheManager;
    private final BookInfoCacheManager bookInfoCacheManager;


    /**
     * 看小说某章节内容
     * @param chapterId 章节ID
     * @return 该章节小说内容
     */
    @Override
    public RestResp<BookContentAboutRespDto> getBookContentAbout(Long chapterId) {
        log.debug("userId:{}", UserHolder.getUserId());

        BookChapterRespDto bookChapter = bookChapterCacheManager.getChapter(chapterId);

        String bookContent = bookContentCacheManager.getBookContent(chapterId);

        BookInfoRespDto bookInfo = bookInfoCacheManager.getBookInfo(bookChapter.getBookId());

        return RestResp.ok(
                BookContentAboutRespDto.builder()
                        .bookInfo(bookInfo)
                        .chapterInfo(bookChapter)
                        .bookContent(bookContent)
                        .build()                    // 小说信息 章节信息 章节内容
        );

    }
    /**!!!!!!!!!!!!!!!!!还没写完！！！！！！！
     * 看下一章？？？
     * next_chapter_id/{chapterId}
     */
    // 没必要再实现一遍getBookContentAbout（Long chapter--），只需要调用一下getBookContentAbout就可以？
//    public Long getNextChapter(Long chapterId) {
//        Long nextChapterId = chapterId++;
//        getBookContentAbout(nextChapterId);
//    }



    /**
     * 看上一章？？？
     * pre_chapter_id/{chapterId}
     */

    /**
     * 看目录
     * http://localhost:1024/#/chapter_list/1357668191920263169
     * chapter/list
     */




}
