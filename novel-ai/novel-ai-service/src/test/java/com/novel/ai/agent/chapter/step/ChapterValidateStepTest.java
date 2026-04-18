package com.novel.ai.agent.chapter.step;

import com.novel.ai.agent.chapter.ChapterAuditContext;
import com.novel.ai.agent.core.StepResult;
import com.novel.book.dto.req.ChapterAuditReqDto;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChapterValidateStepTest {

    private final ChapterValidateStep step = new ChapterValidateStep();

    @Test
    void short_circuits_when_request_is_null() {
        ChapterAuditContext ctx = new ChapterAuditContext(null);

        StepResult r = step.execute(ctx);

        assertThat(r).isEqualTo(StepResult.SHORT_CIRCUIT);
        assertThat(ctx.getResult().getAuditStatus()).isEqualTo(0);
        assertThat(ctx.getResult().getAuditReason()).contains("请求为空");
    }

    @Test
    void short_circuits_when_content_is_blank() {
        ChapterAuditContext ctx = new ChapterAuditContext(
                ChapterAuditReqDto.builder().bookId(1L).chapterNum(2).content("   ").build());

        StepResult r = step.execute(ctx);

        assertThat(r).isEqualTo(StepResult.SHORT_CIRCUIT);
        assertThat(ctx.getResult().getBookId()).isEqualTo(1L);
        assertThat(ctx.getResult().getChapterNum()).isEqualTo(2);
        assertThat(ctx.getResult().getAuditReason()).isEqualTo("章节内容为空，需要人工审核");
    }

    @Test
    void continues_when_content_present() {
        ChapterAuditContext ctx = new ChapterAuditContext(
                ChapterAuditReqDto.builder().bookId(1L).chapterNum(2).content("正文内容").build());

        StepResult r = step.execute(ctx);

        assertThat(r).isEqualTo(StepResult.CONTINUE);
        assertThat(ctx.getResult()).isNull();
    }
}
