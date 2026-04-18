package com.novel.ai.agent.book;

import com.novel.ai.agent.core.AuditErrorClassifier;
import com.novel.book.dto.req.BookAuditReqDto;
import org.junit.jupiter.api.Test;
import org.springframework.ai.retry.NonTransientAiException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class BookAuditExceptionMapperTest {

    private final BookAuditExceptionMapper mapper = new BookAuditExceptionMapper(new AuditErrorClassifier());

    @Test
    void maps_inspection_failure_to_rejection() {
        BookAuditContext ctx = new BookAuditContext(BookAuditReqDto.builder().id(7L).build());
        mapper.mapToResult(ctx, new NonTransientAiException("DataInspectionFailed: ..."));

        assertThat(ctx.getResult().getId()).isEqualTo(7L);
        assertThat(ctx.getResult().getAuditStatus()).isEqualTo(2);
        assertThat(ctx.getResult().getAiConfidence()).isEqualTo(new BigDecimal("1.0"));
        assertThat(ctx.getResult().getAuditReason()).contains("不当信息");
    }

    @Test
    void maps_generic_error_to_pending_fallback() {
        BookAuditContext ctx = new BookAuditContext(BookAuditReqDto.builder().id(7L).build());
        mapper.mapToResult(ctx, new RuntimeException("timeout"));

        assertThat(ctx.getResult().getAuditStatus()).isEqualTo(0);
        assertThat(ctx.getResult().getAiConfidence()).isEqualTo(new BigDecimal("0.0"));
        assertThat(ctx.getResult().getAuditReason()).contains("AI审核服务异常");
    }

    @Test
    void handles_null_request_gracefully() {
        BookAuditContext ctx = new BookAuditContext(null);
        mapper.mapToResult(ctx, new RuntimeException("boom"));

        assertThat(ctx.getResult().getId()).isNull();
        assertThat(ctx.getResult().getAuditStatus()).isEqualTo(0);
    }
}
