package com.novel.book.service;

import com.novel.book.dao.entity.BookChapter;
import com.novel.book.dto.req.*;
import com.novel.book.dto.resp.BookChapterRespDto;
import com.novel.book.dto.resp.BookInfoRespDto;
import com.novel.common.resp.PageRespDto;
import com.novel.common.resp.RestResp;

public interface BookAuthorService {

    /**
     * 作家新增书籍
     * @param dto 新增书籍请求dto
     * @return 响应
     */
    RestResp<Void> saveBook(BookAddReqDto dto);

    /**
     * 更新书籍信息
     * @param dto 更新书籍请求dto
     * @return 响应
     */
    RestResp<Void> updateBook(BookUptReqDto dto);

    /**
     * 作家新增书籍章节
     * @param dto 新增章节Dto
     * @return void
     */
    RestResp<Void> saveBookChapter(ChapterAddReqDto dto);

    /**
     * 作家小说列表查看
     * @param dto 作家小说列表分页请求
     * @return 分页数据体，其中的每一项都是一个书本信息的响应 DTO
     */
    RestResp<PageRespDto<BookInfoRespDto>> listAuthorBooks(BookPageReqDto dto);

    /**
     * 作家章节列表查看
     * @param dto 作家章节列表分页请求
     * @return 分页数据体，其中的每一项都是一个章节信息的响应 DTO
     */
    RestResp<PageRespDto<BookChapterRespDto>> listBookChapters(ChapterPageReqDto dto);

    /**
     * 作家获取章节内容
     * @param bookId,chapterNum 书籍id，章节号
     * @return 章节内容
     */
    RestResp<BookChapterRespDto> getBookChapter(Long bookId, Integer chapterNum);

    /**
     * 作家删除章节
     * @param dto 删除请求
     * @return void
     */
    RestResp<Void> deleteBookChapter(ChapterDelReqDto dto);

    /**
     * 作家更新书籍章节
     * @param dto 更新dto
     * @return void
     */
    RestResp<Void> updateBookChapter(ChapterUptReqDto dto);

    /**
     * 作家删除书籍
     * @param dto 删除请求
     * @return void
     */
    RestResp<Void> deleteBook(BookDelReqDto dto);

}
