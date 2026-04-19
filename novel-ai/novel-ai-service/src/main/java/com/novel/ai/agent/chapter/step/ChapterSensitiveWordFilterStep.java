package com.novel.ai.agent.chapter.step;

import com.novel.ai.agent.chapter.ChapterAuditContext;
import com.novel.ai.agent.core.AuditStep;
import com.novel.ai.agent.core.StepResult;
import com.novel.ai.config.NovelAiLearningAuditProperties;
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
    private final NovelAiLearningAuditProperties learningAuditProperties;

    @Override
    public String name() {
        return "chapter-sensitive-word-filter";
    }

    @Override
    public StepResult execute(ChapterAuditContext ctx) {
        ChapterAuditReqDto req = ctx.getRequest();
        if (req == null) {
            return StepResult.CONTINUE;
        }
        if (learningAuditProperties.matchesLearningCategory(req.getCategoryId(), req.getCategoryName())) {
            log.info("[ChapterSensitiveWordFilter] 学习资料类跳过本地敏感词库 bookId={} chapterNum={}",
                    req.getBookId(), req.getChapterNum());
            return StepResult.CONTINUE;
        }
        boolean enabled = matcher.isEnabled();
        int titleLen = req.getChapterName() != null ? req.getChapterName().length() : 0;
        int contentLen = req.getContent() != null ? req.getContent().length() : 0;
        // WARN + 与 TextService 相同标签，避免仅 INFO 被全局级别过滤时「搜不到」
        log.warn("SENSITIVE_WORD_FILTER step=chapter-sensitive-word-filter enabled={} titleChars={} contentChars={} bookId={} chapterNum={}",
                enabled, titleLen, contentLen, req.getBookId(), req.getChapterNum());
        log.info("[ChapterSensitiveWordFilter] matcherEnabled={} titleChars={} contentChars={} bookId={} chapterNum={}",
                enabled, titleLen, contentLen, req.getBookId(), req.getChapterNum());

        if (!enabled) {
            // INFO：生产默认可见；未启用时必然走后续 AI，与「本地违禁词短路」预期不符时先看这条
            log.warn("[ChapterSensitiveWordFilter] 匹配器未启用（novel.ai.sensitive-filter.enabled=false 或字典为空/资源不存在），"
                    + "跳过本地 AC，将走全文 AI 审核。dictionaryPath 见启动日志 [SensitiveWord]");
            return StepResult.CONTINUE;
        }
        // 标题先扫：命中则不再扫正文（省一次 O(n)）；理由最多展示 MAX_HITS_IN_REASON 条，提前结束扫描
        List<String> hits = new ArrayList<>(matcher.findHitsUpTo(req.getChapterName(), MAX_HITS_IN_REASON));
        if (hits.isEmpty()) {
            hits.addAll(matcher.findHitsUpTo(req.getContent(), MAX_HITS_IN_REASON));
        }

        if (hits.isEmpty()) {
            log.info("[ChapterSensitiveWordFilter] 标题+正文未命中本地词库，继续 RAG/LLM（非违禁词短路） bookId={} chapterNum={}",
                    req.getBookId(), req.getChapterNum());
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
                .sensitiveWordHits(List.copyOf(hits))
                .build();
        ctx.setResult(resp);
        return StepResult.SHORT_CIRCUIT;
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
