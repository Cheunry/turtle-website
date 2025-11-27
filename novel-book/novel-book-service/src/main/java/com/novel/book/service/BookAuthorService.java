package com.novel.book.service;

import com.novel.book.dto.req.BookAddReqDto;
import com.novel.book.dto.req.BookPageReqDto;
import com.novel.book.dto.resp.BookInfoRespDto;
import com.novel.common.resp.PageRespDto;
import com.novel.common.resp.RestResp;

public interface BookAuthorService {

    RestResp<Void> saveBook(BookAddReqDto dto);

    RestResp<PageRespDto<BookInfoRespDto>> listAuthorBooks(BookPageReqDto dto);
}
