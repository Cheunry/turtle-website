package com.novel.ai.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NovelAiLearningAuditPropertiesTest {

    @Test
    void matchesByPrimaryCategoryId() {
        NovelAiLearningAuditProperties p = new NovelAiLearningAuditProperties();
        p.setCategoryId(8L);
        assertThat(p.matchesLearningCategory(8L, null)).isTrue();
        assertThat(p.matchesLearningCategory(8L, "玄幻奇幻")).isTrue();
    }

    @Test
    void matchesByExtraCategoryId() {
        NovelAiLearningAuditProperties p = new NovelAiLearningAuditProperties();
        p.setCategoryId(8L);
        p.setExtraCategoryIds(List.of(99L));
        assertThat(p.matchesLearningCategory(99L, null)).isTrue();
        assertThat(p.matchesLearningCategory(7L, null)).isFalse();
    }

    @Test
    void matchesByCategoryNameWhenIdMissing() {
        NovelAiLearningAuditProperties p = new NovelAiLearningAuditProperties();
        assertThat(p.matchesLearningCategory(null, "学习资料")).isTrue();
        assertThat(p.matchesLearningCategory(null, " 学习资料 ")).isTrue();
        assertThat(p.matchesLearningCategory(null, "学习资料汇编")).isFalse();
    }

    @Test
    void customNameHints() {
        NovelAiLearningAuditProperties p = new NovelAiLearningAuditProperties();
        p.setCategoryNameEqualsHints(List.of("教辅"));
        assertThat(p.matchesLearningCategory(null, "教辅")).isTrue();
        assertThat(p.matchesLearningCategory(null, "学习资料")).isFalse();
    }

    @Test
    void bypassLlmOnlyWhenFlagAndGreenChannel() {
        NovelAiLearningAuditProperties p = new NovelAiLearningAuditProperties();
        p.setGreenChannelSkipLlmForLearning(true);
        p.getGreenChannelAuthorIds().add(2L);
        assertThat(p.shouldBypassLlmForLearning(8L, null, 2L)).isTrue();
        assertThat(p.shouldBypassLlmForLearning(8L, null, 3L)).isFalse();
        assertThat(p.shouldBypassLlmForLearning(1L, null, 2L)).isFalse();
        p.setGreenChannelSkipLlmForLearning(false);
        assertThat(p.shouldBypassLlmForLearning(8L, null, 2L)).isFalse();
    }
}
