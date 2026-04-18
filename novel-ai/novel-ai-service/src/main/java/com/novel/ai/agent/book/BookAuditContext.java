package com.novel.ai.agent.book;

import com.novel.ai.agent.core.AuditContext;
import com.novel.ai.model.AuditDecisionAiOutput;
import com.novel.book.dto.req.BookAuditReqDto;
import com.novel.book.dto.resp.BookAuditRespDto;
import lombok.Getter;
import lombok.Setter;

/**
 * 书籍审核流水线上下文。
 * <p>
 * 阶段 2-C 重构后，RAG 召回不再落到 Context 字段里——改由
 * {@code RetrievalAugmentationAdvisor} 在 LLM 调用前自动注入 user prompt，
 * Pipeline Step 不再承载"判例字符串"状态，Context 只保留 LLM 结构化输出这一块业务结果。
 */
@Getter
@Setter
public class BookAuditContext extends AuditContext<BookAuditReqDto, BookAuditRespDto> {

    /** LLM 结构化输出的审核决定，成功调用后填充。 */
    private AuditDecisionAiOutput aiOutput;

    public BookAuditContext(BookAuditReqDto request) {
        super(request, "audit_book");
    }
}
