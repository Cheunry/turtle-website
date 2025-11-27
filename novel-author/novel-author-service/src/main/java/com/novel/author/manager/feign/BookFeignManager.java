package com.novel.author.manager.feign;

import com.novel.author.dto.AuthorInfoDto;
import com.novel.author.manager.cache.AuthorCacheManager;
import com.novel.book.dto.req.BookAddReqDto;
import com.novel.book.dto.req.BookPageReqDto;
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

    public RestResp<Void> publishBook(BookAddReqDto dto) {
        AuthorInfoDto author = authorCacheManager.getAuthorInfoByUserId(UserHolder.getUserId());
        dto.setAuthorId(author.getId());
        dto.setPenName(author.getPenName());
        return bookFeign.publishBook(dto);
    }

    public RestResp<PageRespDto<BookInfoRespDto>> listPublishBooks(BookPageReqDto dto) {
        authorCacheManager.getAuthorInfoByUserId(UserHolder.getUserId());
        return bookFeign.listPublishBooks(dto);
    }



}
