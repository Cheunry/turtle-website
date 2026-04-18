package com.novel.ai.agent.book;

import com.novel.ai.agent.core.AbstractAuditExceptionMapper;
import com.novel.ai.agent.core.AuditErrorClassifier;
import com.novel.book.dto.resp.BookAuditRespDto;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 把书籍审核流水线中的异常翻译为 {@link BookAuditRespDto}。
 * <ul>
 *     <li>内容安全拦截 → auditStatus=2，置信度=1.0；</li>
 *     <li>其他异常 → auditStatus=0，置信度=0.0，进入人工审核。</li>
 * </ul>
 */
@Component
public class BookAuditExceptionMapper extends AbstractAuditExceptionMapper<BookAuditContext> {

    public BookAuditExceptionMapper(AuditErrorClassifier classifier) {
        super(classifier);
    }

    @Override
    protected Object buildInspectionRejectedResult(BookAuditContext ctx) {
        return BookAuditRespDto.builder()
                .id(ctx.getRequest() != null ? ctx.getRequest().getId() : null)
                .auditStatus(2)
                .aiConfidence(new BigDecimal("1.0"))
                .auditReason("内容包含不当信息，不符合平台规范")
                .build();
    }

    @Override
    protected Object buildFallbackResult(BookAuditContext ctx, Exception error) {
        return BookAuditRespDto.builder()
                .id(ctx.getRequest() != null ? ctx.getRequest().getId() : null)
                .auditStatus(0)
                .aiConfidence(new BigDecimal("0.0"))
                .auditReason("AI审核服务异常")
                .build();
    }
}
