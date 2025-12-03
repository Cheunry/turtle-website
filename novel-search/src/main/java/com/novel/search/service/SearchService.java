package com.novel.search.service;

import com.novel.book.dto.req.BookSearchReqDto;
import com.novel.book.dto.resp.BookInfoRespDto;
import com.novel.common.resp.PageRespDto;
import com.novel.common.resp.RestResp;

public interface SearchService {

    RestResp<PageRespDto<BookInfoRespDto>> searchBooks(BookSearchReqDto bookSearchReqDto);
}
