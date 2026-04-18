package com.novel.ai.agent.chapter.step;

import com.novel.ai.agent.chapter.ChapterAuditContext;
import com.novel.ai.agent.core.AuditStep;
import com.novel.ai.agent.core.StepResult;
import com.novel.ai.sensitive.SensitiveWordMatcher;
import com.novel.book.dto.req.ChapterAuditReqDto;
import com.novel.book.dto.resp.ChapterAuditRespDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 章节审核前置敏感词过滤：扫描章节标题 + 正文，命中即短路为"审核不通过"，
 * 跳过后续切分 / RAG / LLM 调用，大幅降低成本与延迟。
 *
 * <p>对全量正文（可能上万字）走一次 AC 扫描仍是 O(n)，不会成为瓶颈。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChapterSensitiveWordFilterStep implements AuditStep<ChapterAuditContext> {

    private static final BigDecimal HIT_CONFIDENCE = new BigDecimal("1.00");
    private static final int MAX_HITS_IN_REASON = 5;

    private final SensitiveWordMatcher matcher;

    @Override
    public String name() {
        return "chapter-sensitive-word-filter";
    }

    @Override
    public StepResult execute(ChapterAuditContext ctx) {
        if (!matcher.isEnabled()) {
            return StepResult.CONTINUE;
        }
        ChapterAuditReqDto req = ctx.getRequest();
        List<String> hits = new ArrayList<>();
        collect(matcher.findAll(req.getChapterName()), hits);
        collect(matcher.findAll(req.getContent()), hits);

        if (hits.isEmpty()) {
            return StepResult.CONTINUE;
        }

        String reason = buildReason(hits);
        log.info("[ChapterSensitiveWordFilter] 命中敏感词，直接拒审 bookId={} chapterNum={} hits={}",
                req.getBookId(), req.getChapterNum(), hits);
        ChapterAuditRespDto resp = ChapterAuditRespDto.builder()
                .bookId(req.getBookId())
                .chapterNum(req.getChapterNum())
                .auditStatus(2)
                .aiConfidence(HIT_CONFIDENCE)
                .auditReason(reason)
                .build();
        ctx.setResult(resp);
        return StepResult.SHORT_CIRCUIT;
    }

    private void collect(List<String> source, List<String> target) {
        for (String hit : source) {
            if (!target.contains(hit)) {
                target.add(hit);
            }
        }
    }

    private String buildReason(List<String> hits) {
        List<String> shown = hits.size() > MAX_HITS_IN_REASON
                ? hits.subList(0, MAX_HITS_IN_REASON) : hits;
        StringBuilder sb = new StringBuilder("命中敏感词，系统直接拒审：")
                .append(String.join("、", shown));
        if (hits.size() > MAX_HITS_IN_REASON) {
            sb.append(" 等共 ").append(hits.size()).append(" 项");
        }
        return sb.toString();
    }
}
