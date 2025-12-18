package com.novel.user.feign;

import com.novel.book.dto.req.BookCommentReqDto;
import com.novel.book.feign.BookFeign;
import com.novel.common.auth.UserHolder;
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

    public RestResp<List<BookInfoRespDto>> listBookInfoByIds(List<Long> bookIds) {
        return bookFeign.listBookInfoByIds(bookIds);
    }

    public RestResp<List<BookInfoRespDto>> listBookInfoByIdsForBookshelf(List<Long> bookIds) {
        return bookFeign.listBookInfoByIdsForBookshelf(bookIds);
    }
}