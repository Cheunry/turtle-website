package com.novel.ai.agent.chapter.support;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChapterContentSplitterTest {

    private final ChapterContentSplitter splitter = new ChapterContentSplitter();

    @Test
    void returns_empty_list_when_content_is_null() {
        assertThat(splitter.split(null, 100)).isEmpty();
    }

    @Test
    void returns_single_segment_when_content_within_limit() {
        List<String> segments = splitter.split("短内容。", 100);
        assertThat(segments).containsExactly("短内容。");
    }

    @Test
    void splits_into_multiple_segments_when_exceeds_limit() {
        // 250 字符，限制 100 → 至少 3 段
        String content = "a".repeat(250);
        List<String> segments = splitter.split(content, 100);
        assertThat(segments).hasSizeGreaterThanOrEqualTo(3);
        assertThat(String.join("", segments)).isEqualTo(content);
    }

    @Test
    void prefers_sentence_boundary_when_available_within_fallback_window() {
        // 前 80 字符无句末标点，第 90 个位置放"。"；maxLength=100 时应该回退到 90 处断开
        String head = "a".repeat(89) + "。";
        String tail = "b".repeat(50);
        String content = head + tail;

        List<String> segments = splitter.split(content, 100);

        assertThat(segments).hasSize(2);
        assertThat(segments.get(0)).isEqualTo(head);
        assertThat(segments.get(1)).isEqualTo(tail);
    }

    @Test
    void forced_break_when_no_sentence_boundary_in_fallback_window() {
        // 前 500 字符全无标点 → 切分点无回退余地，按 maxLength 硬切
        String content = "x".repeat(500);
        List<String> segments = splitter.split(content, 100);

        assertThat(segments).hasSize(5);
        segments.forEach(s -> assertThat(s).hasSize(100));
    }

    @Test
    void supports_all_supported_break_chars() {
        String content = "abcd！" + "x".repeat(200);
        List<String> segments = splitter.split(content, 10);
        assertThat(segments.get(0)).isEqualTo("abcd！");
    }

    @Test
    void reassembly_always_equals_original_content() {
        String content = "一段中文。另一句？还有一句！\n这是最后一句内容结尾。" + "y".repeat(80);
        for (int limit : new int[]{5, 20, 50, 100, 200}) {
            assertThat(String.join("", splitter.split(content, limit)))
                    .as("limit=%d 时回拼应等于原文", limit)
                    .isEqualTo(content);
        }
    }
}
