package com.novel.ai.agent.chapter;

import com.novel.ai.agent.core.AuditErrorClassifier;
import com.novel.book.dto.req.ChapterAuditReqDto;
import org.junit.jupiter.api.Test;
import org.springframework.ai.retry.NonTransientAiException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ChapterAuditExceptionMapperTest {

    private final ChapterAuditExceptionMapper mapper = new ChapterAuditExceptionMapper(new AuditErrorClassifier());

    @Test
    void maps_inspection_failure_to_rejection() {
        ChapterAuditContext ctx = new ChapterAuditContext(
                ChapterAuditReqDto.builder().bookId(1L).chapterNum(3).build());
        mapper.mapToResult(ctx, new NonTransientAiException("inappropriate content"));

        assertThat(ctx.getResult().getBookId()).isEqualTo(1L);
        assertThat(ctx.getResult().getChapterNum()).isEqualTo(3);
        assertThat(ctx.getResult().getAuditStatus()).isEqualTo(2);
        assertThat(ctx.getResult().getAiConfidence()).isEqualTo(new BigDecimal("1.0"));
    }

    @Test
    void maps_generic_error_to_pending_fallback() {
        ChapterAuditContext ctx = new ChapterAuditContext(
                ChapterAuditReqDto.builder().bookId(1L).chapterNum(3).build());
        mapper.mapToResult(ctx, new RuntimeException("feign exploded"));

        assertThat(ctx.getResult().getAuditStatus()).isEqualTo(0);
        assertThat(ctx.getResult().getAiConfidence()).isEqualTo(new BigDecimal("0.0"));
        assertThat(ctx.getResult().getAuditReason()).contains("AI审核服务异常");
    }

    @Test
    void handles_null_request_gracefully() {
        ChapterAuditContext ctx = new ChapterAuditContext(null);
        mapper.mapToResult(ctx, new RuntimeException("boom"));

        assertThat(ctx.getResult().getBookId()).isNull();
        assertThat(ctx.getResult().getChapterNum()).isNull();
        assertThat(ctx.getResult().getAuditStatus()).isEqualTo(0);
    }
}
