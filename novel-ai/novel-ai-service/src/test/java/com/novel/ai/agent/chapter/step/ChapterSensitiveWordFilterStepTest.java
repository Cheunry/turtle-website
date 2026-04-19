package com.novel.ai.agent.chapter.step;

import com.novel.ai.agent.chapter.ChapterAuditContext;
import com.novel.ai.agent.core.StepResult;
import com.novel.ai.config.NovelAiLearningAuditProperties;
import com.novel.ai.sensitive.SensitiveWordMatcher;
import com.novel.book.dto.req.ChapterAuditReqDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChapterSensitiveWordFilterStepTest {

    @Test
    void shouldShortCircuitWhenHitsExist() {
        SensitiveWordMatcher matcher = mock(SensitiveWordMatcher.class);
        when(matcher.isEnabled()).thenReturn(true);
        when(matcher.findHitsUpTo(any(), anyInt())).thenReturn(List.of("血腥"));

        ChapterSensitiveWordFilterStep step = new ChapterSensitiveWordFilterStep(matcher, new NovelAiLearningAuditProperties());
        ChapterAuditContext ctx = new ChapterAuditContext(ChapterAuditReqDto.builder()
                .bookId(1L).chapterNum(1).chapterName("血腥章").content("内容").build());

        assertThat(step.execute(ctx)).isEqualTo(StepResult.SHORT_CIRCUIT);
        assertThat(ctx.getResult()).isNotNull();
        assertThat(ctx.getResult().getAuditStatus()).isEqualTo(2);
        assertThat(ctx.getResult().getAuditReason()).contains("血腥");
    }

    @Test
    void shouldContinueWhenNoHit() {
        SensitiveWordMatcher matcher = mock(SensitiveWordMatcher.class);
        when(matcher.isEnabled()).thenReturn(true);
        when(matcher.findHitsUpTo(any(), anyInt())).thenReturn(List.of());

        ChapterSensitiveWordFilterStep step = new ChapterSensitiveWordFilterStep(matcher, new NovelAiLearningAuditProperties());
        ChapterAuditContext ctx = new ChapterAuditContext(ChapterAuditReqDto.builder()
                .bookId(1L).chapterNum(1).chapterName("正常").content("正常").build());

        assertThat(step.execute(ctx)).isEqualTo(StepResult.CONTINUE);
        assertThat(ctx.getResult()).isNull();
    }

    @Test
    void shouldContinueWhenMatcherDisabled() {
        SensitiveWordMatcher matcher = mock(SensitiveWordMatcher.class);
        when(matcher.isEnabled()).thenReturn(false);

        ChapterSensitiveWordFilterStep step = new ChapterSensitiveWordFilterStep(matcher, new NovelAiLearningAuditProperties());
        ChapterAuditContext ctx = new ChapterAuditContext(ChapterAuditReqDto.builder()
                .bookId(1L).chapterNum(1).chapterName("x").content("y").build());

        assertThat(step.execute(ctx)).isEqualTo(StepResult.CONTINUE);
        assertThat(ctx.getResult()).isNull();
    }

    @Test
    void learningCategorySkipsLocalDictionary() {
        SensitiveWordMatcher matcher = mock(SensitiveWordMatcher.class);
        NovelAiLearningAuditProperties learning = new NovelAiLearningAuditProperties();
        ChapterSensitiveWordFilterStep step = new ChapterSensitiveWordFilterStep(matcher, learning);
        ChapterAuditContext ctx = new ChapterAuditContext(ChapterAuditReqDto.builder()
                .bookId(1L).chapterNum(1).categoryId(learning.getCategoryId()).chapterName("x").content("y").build());

        assertThat(step.execute(ctx)).isEqualTo(StepResult.CONTINUE);
        assertThat(ctx.getResult()).isNull();
    }

    @Test
    void learningCategorySkipsByNameWhenCategoryIdMissing() {
        SensitiveWordMatcher matcher = mock(SensitiveWordMatcher.class);
        ChapterSensitiveWordFilterStep step = new ChapterSensitiveWordFilterStep(matcher, new NovelAiLearningAuditProperties());
        ChapterAuditContext ctx = new ChapterAuditContext(ChapterAuditReqDto.builder()
                .bookId(1L).chapterNum(1).categoryId(null).categoryName("学习资料").chapterName("x").content("y").build());

        assertThat(step.execute(ctx)).isEqualTo(StepResult.CONTINUE);
        assertThat(ctx.getResult()).isNull();
    }
}
