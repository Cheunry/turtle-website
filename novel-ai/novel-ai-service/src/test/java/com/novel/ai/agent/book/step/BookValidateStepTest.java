package com.novel.ai.agent.book.step;

import com.novel.ai.agent.book.BookAuditContext;
import com.novel.ai.agent.core.StepResult;
import com.novel.book.dto.req.BookAuditReqDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class BookValidateStepTest {

    private final BookValidateStep step = new BookValidateStep();

    @Test
    void short_circuits_when_request_is_null() {
        BookAuditContext ctx = new BookAuditContext(null);

        StepResult r = step.execute(ctx);

        assertThat(r).isEqualTo(StepResult.SHORT_CIRCUIT);
        assertThat(ctx.getResult().getAuditStatus()).isEqualTo(0);
        assertThat(ctx.getResult().getAiConfidence()).isEqualTo(new BigDecimal("0.0"));
        assertThat(ctx.getResult().getAuditReason()).contains("请求为空");
    }

    @Test
    void continues_when_request_present() {
        BookAuditContext ctx = new BookAuditContext(BookAuditReqDto.builder().id(1L).bookName("x").bookDesc("y").build());

        StepResult r = step.execute(ctx);

        assertThat(r).isEqualTo(StepResult.CONTINUE);
        assertThat(ctx.getResult()).isNull();
    }
}
