package com.novel.author.manager.feign;

import com.novel.author.dto.AuthorInfoDto;
import com.novel.author.manager.cache.AuthorCacheManager;
import com.novel.book.dto.req.*;
import com.novel.book.dto.resp.BookChapterRespDto;
import com.novel.book.dto.resp.BookInfoRespDto;
import com.novel.book.manager.feign.BookFeign;
import com.novel.common.auth.UserHolder;
import com.novel.common.resp.PageRespDto;
import com.novel.common.resp.RestResp;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class BookFeignManager {

    private final BookFeign bookFeign;
    private final AuthorCacheManager authorCacheManager;

    /**
     * 作家发布书籍
     * @param dto 新增书籍响应体
     * @return
     */
    public RestResp<Void> publishBook(BookAddReqDto dto) {

        AuthorInfoDto author = authorCacheManager.getAuthorInfoByUserId(UserHolder.getUserId());

        dto.setAuthorId(author.getId());
        dto.setPenName(author.getPenName());

        return bookFeign.publishBook(dto);
    }

    /**
     * 作家章节发布接口
     * @param dto 新增章节响应体
     * @return
     */
    public RestResp<Void> publishBookChapter(ChapterAddReqDto dto) {
        return bookFeign.publishBookChapter(dto);
    }

    /**
     * 作家书籍列表
     * @param dto 分页dto
     * @return
     */
    public RestResp<PageRespDto<BookInfoRespDto>> listPublishBooks(BookPageReqDto dto) {

        authorCacheManager.getAuthorInfoByUserId(UserHolder.getUserId());

        return bookFeign.listPublishBooks(dto);
    }

    /**
     * 作家章节列表
     */
    public RestResp<PageRespDto<BookChapterRespDto>> listPublishBookChapters(ChapterPageReqDto dto) {

        return bookFeign.listPublishBookChapters(dto);
    }

    /**
     * 获取单个章节信息
     */
    public RestResp<BookChapterRespDto> getBookChapter(Long bookId, Integer chapterNum) {
        return bookFeign.getBookChapter(bookId, chapterNum);
    }

    /**
     * 更新某章节信息
     */
    public RestResp<Void> updateBookChapter(ChapterUptReqDto dto) {
        dto.setAuthorId(UserHolder.getAuthorId());
        return bookFeign.updateBookChapter(dto);
    }

    /**
     * 删除某章节
     */
    public RestResp<Void> deleteBookChapter(ChapterDelReqDto dto) {
        // 确保 AuthorId 被正确设置，防止越权删除
        dto.setAuthorId(UserHolder.getAuthorId()); 
        return bookFeign.deleteBookChapter(dto);
    }



}
