package com.novel.book.feign;

import com.novel.ai.feign.AiFeign;
import com.novel.book.dto.req.BookAuditReqDto;
import com.novel.book.dto.req.ChapterAuditReqDto;
import com.novel.book.dto.resp.BookAuditRespDto;
import com.novel.book.dto.resp.ChapterAuditRespDto;
import com.novel.common.resp.RestResp;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class AiFeignManager {

    private final AiFeign aiFeign;

    /**
     * 审核书籍内容
     * @param req 审核请求
     * @return 审核结果
     */
    public RestResp<BookAuditRespDto> auditBook(BookAuditReqDto req) {
        return aiFeign.auditBook(req);
    }

    /**
     * 审核章节内容
     * @param req 审核请求
     * @return 审核结果
     */
    public RestResp<ChapterAuditRespDto> auditChapter(ChapterAuditReqDto req) {
        return aiFeign.auditChapter(req);
    }
}