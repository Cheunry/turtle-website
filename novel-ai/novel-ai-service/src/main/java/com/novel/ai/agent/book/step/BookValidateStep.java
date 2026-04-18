package com.novel.ai.agent.book.step;

import com.novel.ai.agent.book.BookAuditContext;
import com.novel.ai.agent.core.AuditStep;
import com.novel.ai.agent.core.StepResult;
import com.novel.book.dto.resp.BookAuditRespDto;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 书籍审核的入口校验：请求体为空时直接短路为"待审核"，避免下游 Step 遇到 NPE。
 * 注意此处不对 bookName/bookDesc 做强制非空校验，原实现允许这两者为空（会被 RAG 与
 * prompt 端安全处理），保持兼容。
 */
@Component
public class BookValidateStep implements AuditStep<BookAuditContext> {

    @Override
    public StepResult execute(BookAuditContext ctx) {
        if (ctx.getRequest() == null) {
            ctx.setResult(BookAuditRespDto.builder()
                    .auditStatus(0)
                    .aiConfidence(new BigDecimal("0.0"))
                    .auditReason("请求为空")
                    .build());
            return StepResult.SHORT_CIRCUIT;
        }
        return StepResult.CONTINUE;
    }
}
