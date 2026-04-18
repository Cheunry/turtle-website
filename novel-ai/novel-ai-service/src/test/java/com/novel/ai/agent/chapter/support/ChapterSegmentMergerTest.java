package com.novel.ai.agent.chapter.support;

import com.novel.book.dto.resp.ChapterAuditRespDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChapterSegmentMergerTest {

    private final ChapterSegmentMerger merger = new ChapterSegmentMerger();

    @Test
    void empty_segments_fall_back_to_pending_placeholder() {
        ChapterAuditRespDto resp = merger.merge(Collections.emptyList(), 1L, 2);
        assertThat(resp.getBookId()).isEqualTo(1L);
        assertThat(resp.getChapterNum()).isEqualTo(2);
        assertThat(resp.getAuditStatus()).isEqualTo(0);
        assertThat(resp.getAiConfidence()).isEqualTo(new BigDecimal("0.0"));
        assertThat(resp.getAuditReason()).isEqualTo("分段审核结果为空");
    }

    @Test
    void all_pass_produces_friendly_summary() {
        List<ChapterAuditRespDto> passed = List.of(
                seg(1, new BigDecimal("0.90"), "通过-1"),
                seg(1, new BigDecimal("0.80"), "通过-2"),
                seg(1, new BigDecimal("0.70"), "通过-3"));

        ChapterAuditRespDto resp = merger.merge(passed, 1L, 2);

        assertThat(resp.getAuditStatus()).isEqualTo(1);
        assertThat(resp.getAiConfidence()).isEqualTo(new BigDecimal("0.80"));
        assertThat(resp.getAuditReason()).isEqualTo("共3段内容，均已审核通过，符合网络文学平台规范");
    }

    @Test
    void any_failure_makes_overall_rejected() {
        List<ChapterAuditRespDto> segments = List.of(
                seg(1, new BigDecimal("0.90"), "通过"),
                seg(2, new BigDecimal("0.10"), "涉及暴力描写"),
                seg(1, new BigDecimal("0.80"), "通过"));

        ChapterAuditRespDto resp = merger.merge(segments, 1L, 2);

        assertThat(resp.getAuditStatus()).isEqualTo(2);
        assertThat(resp.getAuditReason()).contains("不通过段：", "第2段：涉及暴力描写", "其余2段通过");
    }

    @Test
    void pending_wins_over_passed_but_loses_to_failed() {
        List<ChapterAuditRespDto> segments = List.of(
                seg(1, new BigDecimal("0.90"), "通过"),
                seg(0, new BigDecimal("0.50"), "需人工复核"));

        ChapterAuditRespDto resp = merger.merge(segments, 1L, 2);

        assertThat(resp.getAuditStatus()).isEqualTo(0);
        assertThat(resp.getAuditReason())
                .contains("待审核段：", "第2段：需人工复核");
    }

    @Test
    void confidence_averages_only_non_null_values() {
        List<ChapterAuditRespDto> segments = List.of(
                seg(1, new BigDecimal("1.00"), "a"),
                seg(1, null, "b"),
                seg(1, new BigDecimal("0.60"), "c"));

        ChapterAuditRespDto resp = merger.merge(segments, 1L, 2);

        // (1.00 + 0.60) / 2 = 0.80
        assertThat(resp.getAiConfidence()).isEqualTo(new BigDecimal("0.80"));
    }

    @Test
    void reason_is_truncated_when_exceeds_limit() {
        String longReason = "违规理由".repeat(80); // ~320 chars per segment
        List<ChapterAuditRespDto> segments = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            segments.add(seg(2, new BigDecimal("0.10"), longReason));
        }

        ChapterAuditRespDto resp = merger.merge(segments, 1L, 2);

        assertThat(resp.getAuditReason().length()).isLessThanOrEqualTo(500);
    }

    private static ChapterAuditRespDto seg(int status, BigDecimal confidence, String reason) {
        return ChapterAuditRespDto.builder()
                .bookId(1L)
                .chapterNum(1)
                .auditStatus(status)
                .aiConfidence(confidence)
                .auditReason(reason)
                .build();
    }
}
