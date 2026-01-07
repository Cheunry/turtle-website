package com.novel.user.feign;

import com.novel.book.dto.req.*;
import com.novel.book.dto.resp.BookChapterRespDto;
import com.novel.book.feign.BookFeign;
import com.novel.common.auth.UserHolder;
import com.novel.common.resp.PageRespDto;
import com.novel.common.resp.RestResp;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

import com.novel.book.dto.resp.BookInfoRespDto;

/**
 * 小说微服务调用 Feign 客户端管理
 */
@Component
@AllArgsConstructor
public class BookFeignManager {

    private final BookFeign bookFeign;

    public RestResp<Void> publishComment(BookCommentReqDto dto) {
        dto.setUserId(UserHolder.getUserId());
        return bookFeign.publishComment(dto);
    }

    public RestResp<Void> updateComment(BookCommentReqDto dto) {
        dto.setUserId(UserHolder.getUserId());
        return bookFeign.updateComment(dto);
    }

    public RestResp<Void> deleteComment(BookCommentReqDto dto) {
        dto.setUserId(UserHolder.getUserId());
        return bookFeign.deleteComment(dto);
    }


    public RestResp<List<BookInfoRespDto>> listBookInfoByIdsForBookshelf(List<Long> bookIds) {
        return bookFeign.listBookInfoByIdsForBookshelf(bookIds);
    }

    /**
     * 作家书籍列表
     * @param dto 分页dto
     * @return 书籍列表
     */
    public RestResp<PageRespDto<BookInfoRespDto>> listPublishBooks(BookPageReqDto dto) {
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
     * 删除某章节
     */
    public RestResp<Void> deleteBookChapter(ChapterDelReqDto dto) {
        // 确保 AuthorId 被正确设置，防止越权删除
        dto.setAuthorId(UserHolder.getAuthorId());
        return bookFeign.deleteBookChapter(dto);
    }

    /**
     * 删除书籍
     */
    public RestResp<Void> deleteBook(BookDelReqDto dto) {
        // 确保 AuthorId 被正确设置，防止越权删除
        dto.setAuthorId(UserHolder.getAuthorId());
        return bookFeign.deleteBook(dto);
    }

    /**
     * 获取书籍详情（用于编辑，不过滤审核状态）
     */
    public RestResp<BookInfoRespDto> getBookByIdForAuthor(Long bookId) {
        Long authorId = UserHolder.getAuthorId();
        return bookFeign.getBookByIdForAuthor(bookId, authorId);
    }
}
