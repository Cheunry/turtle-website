package com.novel.ai.service;

import com.novel.ai.dto.req.TextPolishReqDto;
import com.novel.ai.dto.resp.TextPolishRespDto;
import com.novel.book.dto.req.BookAuditReqDto;
import com.novel.book.dto.req.ChapterAuditReqDto;
import com.novel.book.dto.resp.BookAuditRespDto;
import com.novel.book.dto.resp.ChapterAuditRespDto;
import com.novel.common.resp.RestResp;

public interface TextService {

    /**
     * AI审核书籍（小说名和简介）
     */
    RestResp<BookAuditRespDto> auditBook(BookAuditReqDto reqDto);

    /**
     * AI审核章节（章节名和内容）
     */
    RestResp<ChapterAuditRespDto> auditChapter(ChapterAuditReqDto reqDto);

    /**
     * 文本润色
     */
    RestResp<TextPolishRespDto> polishText(TextPolishReqDto reqDto);
}