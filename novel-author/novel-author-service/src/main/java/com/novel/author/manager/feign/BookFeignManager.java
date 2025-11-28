package com.novel.author.manager.feign;

import com.novel.author.dto.AuthorInfoDto;
import com.novel.author.manager.cache.AuthorCacheManager;
import com.novel.book.dto.req.BookAddReqDto;
import com.novel.book.dto.req.BookPageReqDto;
import com.novel.book.dto.req.ChapterAddReqDto;
import com.novel.book.dto.req.ChapterPageReqDto;
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



}
