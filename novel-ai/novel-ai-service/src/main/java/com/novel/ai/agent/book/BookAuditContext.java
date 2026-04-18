package com.novel.ai.agent.book;

import com.novel.ai.agent.core.AuditContext;
import com.novel.ai.model.AuditDecisionAiOutput;
import com.novel.book.dto.req.BookAuditReqDto;
import com.novel.book.dto.resp.BookAuditRespDto;
import lombok.Getter;
import lombok.Setter;

/**
 * 书籍审核流水线上下文。在 {@link AuditContext} 基础上补充书籍审核特有字段：
 * RAG 召回结果与 LLM 结构化输出。
 */
@Getter
@Setter
public class BookAuditContext extends AuditContext<BookAuditReqDto, BookAuditRespDto> {

    /** RAG 检索到的相似判例文本，供 prompt 拼接；无命中时为 ""。 */
    private String similarExperiences = "";

    /** LLM 结构化输出的审核决定，成功调用后填充。 */
    private AuditDecisionAiOutput aiOutput;

    public BookAuditContext(BookAuditReqDto request) {
        super(request, "audit_book");
    }
}
