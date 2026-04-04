package com.novel.search.service;

import com.novel.book.dto.req.BookSearchReqDto;
import com.novel.book.dto.resp.BookInfoRespDto;
import com.novel.common.resp.PageRespDto;
import com.novel.common.resp.RestResp;
import com.novel.search.dto.req.AuditExperienceSearchReqDto;
import com.novel.search.dto.resp.AuditExperienceSearchRespDto;

import java.util.List;

public interface SearchService {

    RestResp<PageRespDto<BookInfoRespDto>> searchBooks(BookSearchReqDto bookSearchReqDto);

    RestResp<List<AuditExperienceSearchRespDto>> searchAuditExperience(AuditExperienceSearchReqDto reqDto);
}
