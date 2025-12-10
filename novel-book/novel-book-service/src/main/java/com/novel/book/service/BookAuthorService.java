package com.novel.book.service;

import com.novel.book.dao.entity.BookChapter;
import com.novel.book.dto.req.*;
import com.novel.book.dto.resp.BookChapterRespDto;
import com.novel.book.dto.resp.BookInfoRespDto;
import com.novel.common.resp.PageRespDto;
import com.novel.common.resp.RestResp;

public interface BookAuthorService {

    RestResp<Void> saveBook(BookAddReqDto dto);

    RestResp<Void> saveBookChapter(ChapterAddReqDto dto);

    RestResp<PageRespDto<BookInfoRespDto>> listAuthorBooks(BookPageReqDto dto);

    RestResp<PageRespDto<BookChapterRespDto>> listBookChapters(ChapterPageReqDto dto);

    RestResp<BookChapterRespDto> getBookChapter(Long bookId, Integer chapterNum);

    RestResp<Void> deleteBookChapter(ChapterDelReqDto dto);

    RestResp<Void> updateBookChapter(ChapterUptReqDto dto);

}
