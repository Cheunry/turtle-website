package com.novel.ai.agent.book.step;

import com.novel.ai.agent.book.BookAuditContext;
import com.novel.ai.agent.core.StepResult;
import com.novel.ai.config.NovelAiLearningAuditProperties;
import com.novel.ai.sensitive.SensitiveWordMatcher;
import com.novel.book.dto.req.BookAuditReqDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BookSensitiveWordFilterStepTest {

    @Test
    void shouldShortCircuitWhenHitsExist() {
        SensitiveWordMatcher matcher = mock(SensitiveWordMatcher.class);
        when(matcher.isEnabled()).thenReturn(true);
        when(matcher.findHitsUpTo(any(), anyInt())).thenReturn(List.of("暴力"));

        BookSensitiveWordFilterStep step = new BookSensitiveWordFilterStep(matcher, new NovelAiLearningAuditProperties());
        BookAuditContext ctx = new BookAuditContext(BookAuditReqDto.builder()
                .id(1L).bookName("暴力小说").bookDesc("充满暴力").build());

        assertThat(step.execute(ctx)).isEqualTo(StepResult.SHORT_CIRCUIT);
        assertThat(ctx.getResult()).isNotNull();
        assertThat(ctx.getResult().getAuditStatus()).isEqualTo(2);
        assertThat(ctx.getResult().getAuditReason()).contains("暴力");
    }

    @Test
    void shouldContinueWhenNoHit() {
        SensitiveWordMatcher matcher = mock(SensitiveWordMatcher.class);
        when(matcher.isEnabled()).thenReturn(true);
        when(matcher.findHitsUpTo(any(), anyInt())).thenReturn(List.of());

        BookSensitiveWordFilterStep step = new BookSensitiveWordFilterStep(matcher, new NovelAiLearningAuditProperties());
        BookAuditContext ctx = new BookAuditContext(BookAuditReqDto.builder()
                .id(1L).bookName("正常").bookDesc("正常").build());

        assertThat(step.execute(ctx)).isEqualTo(StepResult.CONTINUE);
        assertThat(ctx.getResult()).isNull();
    }

    @Test
    void shouldContinueWhenMatcherDisabled() {
        SensitiveWordMatcher matcher = mock(SensitiveWordMatcher.class);
        when(matcher.isEnabled()).thenReturn(false);

        BookSensitiveWordFilterStep step = new BookSensitiveWordFilterStep(matcher, new NovelAiLearningAuditProperties());
        BookAuditContext ctx = new BookAuditContext(BookAuditReqDto.builder()
                .id(1L).bookName("x").bookDesc("y").build());

        assertThat(step.execute(ctx)).isEqualTo(StepResult.CONTINUE);
        assertThat(ctx.getResult()).isNull();
    }

    @Test
    void learningCategorySkipsLocalDictionary() {
        SensitiveWordMatcher matcher = mock(SensitiveWordMatcher.class);
        NovelAiLearningAuditProperties learning = new NovelAiLearningAuditProperties();
        BookSensitiveWordFilterStep step = new BookSensitiveWordFilterStep(matcher, learning);
        BookAuditContext ctx = new BookAuditContext(BookAuditReqDto.builder()
                .id(1L).categoryId(learning.getCategoryId()).bookName("任意").bookDesc("内容").build());

        assertThat(step.execute(ctx)).isEqualTo(StepResult.CONTINUE);
        assertThat(ctx.getResult()).isNull();
    }

    @Test
    void learningCategorySkipsByNameWhenCategoryIdMissing() {
        SensitiveWordMatcher matcher = mock(SensitiveWordMatcher.class);
        BookSensitiveWordFilterStep step = new BookSensitiveWordFilterStep(matcher, new NovelAiLearningAuditProperties());
        BookAuditContext ctx = new BookAuditContext(BookAuditReqDto.builder()
                .id(1L).categoryId(null).categoryName("学习资料").bookName("Java").bookDesc("教程").build());

        assertThat(step.execute(ctx)).isEqualTo(StepResult.CONTINUE);
        assertThat(ctx.getResult()).isNull();
    }
}
