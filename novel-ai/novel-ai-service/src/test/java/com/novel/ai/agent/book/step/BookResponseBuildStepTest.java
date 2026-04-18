package com.novel.ai.agent.book.step;

import com.novel.ai.agent.book.BookAuditContext;
import com.novel.ai.agent.core.StepResult;
import com.novel.ai.agent.support.AuditDecisionResolver;
import com.novel.ai.model.AuditDecisionAiOutput;
import com.novel.book.dto.req.BookAuditReqDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class BookResponseBuildStepTest {

    private final BookResponseBuildStep step = new BookResponseBuildStep(new AuditDecisionResolver());

    @Test
    void builds_response_from_ai_output() {
        BookAuditContext ctx = new BookAuditContext(BookAuditReqDto.builder().id(42L).build());
        ctx.setAiOutput(new AuditDecisionAiOutput(1, 0.87, "合规"));

        StepResult r = step.execute(ctx);

        assertThat(r).isEqualTo(StepResult.CONTINUE);
        assertThat(ctx.getResult().getId()).isEqualTo(42L);
        assertThat(ctx.getResult().getAuditStatus()).isEqualTo(1);
        assertThat(ctx.getResult().getAiConfidence()).isEqualTo(new BigDecimal("0.87"));
        assertThat(ctx.getResult().getAuditReason()).isEqualTo("合规");
    }

    @Test
    void uses_resolver_defaults_when_ai_output_null() {
        BookAuditContext ctx = new BookAuditContext(BookAuditReqDto.builder().id(1L).build());
        ctx.setAiOutput(null);

        step.execute(ctx);

        assertThat(ctx.getResult().getAuditStatus()).isEqualTo(0);
        assertThat(ctx.getResult().getAiConfidence()).isEqualTo(new BigDecimal("0.50"));
        assertThat(ctx.getResult().getAuditReason()).isEqualTo("AI审核完成");
    }
}
