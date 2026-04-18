package com.novel.ai.sensitive;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 敏感词匹配器的核心行为测试：字典加载、多命中去重、大小写折叠、禁用态。
 */
class SensitiveWordMatcherTest {

    private SensitiveWordMatcher build(boolean enabled, boolean ignoreCase, List<String> words) {
        SensitiveWordProperties props = new SensitiveWordProperties();
        props.setEnabled(enabled);
        props.setIgnoreCase(ignoreCase);
        SensitiveWordSource source = () -> words;
        SensitiveWordMatcher matcher = new SensitiveWordMatcher(source, props);
        matcher.init();
        return matcher;
    }

    @Test
    void shouldFindHitsWithDeduplication() {
        SensitiveWordMatcher matcher = build(true, true, List.of("暴力", "血腥"));
        List<String> hits = matcher.findAll("此处充满暴力与血腥，以及更多暴力");
        assertThat(hits).containsExactly("暴力", "血腥");
        assertThat(matcher.hasAny("暴力！")).isTrue();
    }

    @Test
    void shouldMatchCaseInsensitivelyWhenEnabled() {
        SensitiveWordMatcher matcher = build(true, true, List.of("violence"));
        assertThat(matcher.findAll("Pure VIOLENCE ahead")).containsExactly("violence");
    }

    @Test
    void shouldReturnEmptyWhenDisabled() {
        SensitiveWordMatcher matcher = build(false, true, List.of("暴力"));
        assertThat(matcher.isEnabled()).isFalse();
        assertThat(matcher.findAll("暴力")).isEmpty();
        assertThat(matcher.hasAny("暴力")).isFalse();
    }

    @Test
    void shouldDegradeOnEmptyDictionary() {
        SensitiveWordMatcher matcher = build(true, true, List.of());
        assertThat(matcher.isEnabled()).isFalse();
        assertThat(matcher.findAll("任意内容")).isEmpty();
    }

    @Test
    void shouldTolerateNullAndBlankInput() {
        SensitiveWordMatcher matcher = build(true, true, List.of("暴力"));
        assertThat(matcher.findAll(null)).isEmpty();
        assertThat(matcher.findAll("")).isEmpty();
        assertThat(matcher.hasAny(null)).isFalse();
    }

    @Test
    void findHitsUpToStopsAfterDistinctCap() {
        SensitiveWordMatcher matcher = build(true, true, List.of("一", "二", "三"));
        assertThat(matcher.findHitsUpTo("一二三四五六", 2)).containsExactly("一", "二");
    }
}
