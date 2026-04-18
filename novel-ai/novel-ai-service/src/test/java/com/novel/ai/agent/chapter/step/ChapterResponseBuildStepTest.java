package com.novel.ai.agent.chapter.step;

import com.novel.ai.agent.chapter.ChapterAuditContext;
import com.novel.ai.agent.chapter.support.ChapterSegmentMerger;
import com.novel.ai.agent.core.StepResult;
import com.novel.book.dto.req.ChapterAuditReqDto;
import com.novel.book.dto.resp.ChapterAuditRespDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ChapterResponseBuildStepTest {

    private final ChapterResponseBuildStep step = new ChapterResponseBuildStep(new ChapterSegmentMerger());

    @Test
    void single_segment_is_passed_through_verbatim() {
        ChapterAuditContext ctx = new ChapterAuditContext(
                ChapterAuditReqDto.builder().bookId(1L).chapterNum(2).build());
        ctx.getSegmentResults().add(ChapterAuditRespDto.builder()
                .bookId(1L).chapterNum(2)
                .auditStatus(1).aiConfidence(new BigDecimal("0.90")).auditReason("通过原因").build());

        StepResult r = step.execute(ctx);

        assertThat(r).isEqualTo(StepResult.CONTINUE);
        assertThat(ctx.getResult().getAuditStatus()).isEqualTo(1);
        assertThat(ctx.getResult().getAiConfidence()).isEqualTo(new BigDecimal("0.90"));
        assertThat(ctx.getResult().getAuditReason()).isEqualTo("通过原因");
    }

    @Test
    void multi_segment_delegates_to_merger() {
        ChapterAuditContext ctx = new ChapterAuditContext(
                ChapterAuditReqDto.builder().bookId(9L).chapterNum(3).build());
        ctx.getSegmentResults().add(ChapterAuditRespDto.builder()
                .bookId(9L).chapterNum(3).auditStatus(1).aiConfidence(new BigDecimal("1.00")).auditReason("ok").build());
        ctx.getSegmentResults().add(ChapterAuditRespDto.builder()
                .bookId(9L).chapterNum(3).auditStatus(2).aiConfidence(new BigDecimal("0.20")).auditReason("雷人").build());

        step.execute(ctx);

        assertThat(ctx.getResult().getAuditStatus()).isEqualTo(2);
        assertThat(ctx.getResult().getAuditReason()).contains("第2段：雷人");
    }
}
