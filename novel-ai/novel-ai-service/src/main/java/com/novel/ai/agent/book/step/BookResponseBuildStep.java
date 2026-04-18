package com.novel.ai.agent.book.step;

import com.novel.ai.agent.book.BookAuditContext;
import com.novel.ai.agent.core.AuditStep;
import com.novel.ai.agent.core.StepResult;
import com.novel.ai.agent.support.AuditDecisionResolver;
import com.novel.book.dto.resp.BookAuditRespDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 把 {@link BookAuditContext#getAiOutput()} 与请求中的 bookId 合成业务响应。
 */
@Component
@RequiredArgsConstructor
public class BookResponseBuildStep implements AuditStep<BookAuditContext> {

    private final AuditDecisionResolver resolver;

    @Override
    public StepResult execute(BookAuditContext ctx) {
        BookAuditRespDto resp = BookAuditRespDto.builder()
                .id(ctx.getRequest().getId())
                .auditStatus(resolver.resolveStatus(ctx.getAiOutput()))
                .aiConfidence(resolver.resolveConfidence(ctx.getAiOutput()))
                .auditReason(resolver.resolveReason(ctx.getAiOutput()))
                .build();
        ctx.setResult(resp);
        return StepResult.CONTINUE;
    }
}
