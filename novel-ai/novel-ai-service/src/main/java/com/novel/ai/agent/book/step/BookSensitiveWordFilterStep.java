package com.novel.ai.agent.book.step;

import com.novel.ai.agent.book.BookAuditContext;
import com.novel.ai.agent.core.AuditStep;
import com.novel.ai.agent.core.StepResult;
import com.novel.ai.config.NovelAiLearningAuditProperties;
import com.novel.ai.sensitive.SensitiveWordMatcher;
import com.novel.book.dto.req.BookAuditReqDto;
import com.novel.book.dto.resp.BookAuditRespDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 书籍审核前置敏感词过滤：扫描书名 + 描述，命中即短路为"审核不通过"，
 * 跳过后续 RAG / LLM 调用以省成本。
 *
 * <p>{@link SensitiveWordMatcher} 无字典或被禁用时本 Step 静默放行。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookSensitiveWordFilterStep implements AuditStep<BookAuditContext> {

    /** 命中时写入响应的置信度：完全确定，1.0。 */
    private static final BigDecimal HIT_CONFIDENCE = new BigDecimal("1.00");

    /** 命中时展示的最多前 N 个词，防止 reason 过长。 */
    private static final int MAX_HITS_IN_REASON = 5;

    private final SensitiveWordMatcher matcher;
    private final NovelAiLearningAuditProperties learningAuditProperties;

    @Override
    public String name() {
        return "book-sensitive-word-filter";
    }

    @Override
    public StepResult execute(BookAuditContext ctx) {
        BookAuditReqDto req = ctx.getRequest();
        if (req == null) {
            return StepResult.CONTINUE;
        }
        if (learningAuditProperties.matchesLearningCategory(req.getCategoryId(), req.getCategoryName())) {
            log.info("[BookSensitiveWordFilter] 学习资料类跳过本地敏感词库 bookId={}", req.getId());
            return StepResult.CONTINUE;
        }
        boolean enabled = matcher.isEnabled();
        int nameLen = req.getBookName() != null ? req.getBookName().length() : 0;
        int descLen = req.getBookDesc() != null ? req.getBookDesc().length() : 0;
        log.warn("SENSITIVE_WORD_FILTER step=book-sensitive-word-filter enabled={} nameChars={} descChars={} bookId={}",
                enabled, nameLen, descLen, req.getId());
        log.info("[BookSensitiveWordFilter] matcherEnabled={} bookNameChars={} bookDescChars={} bookId={}",
                enabled, nameLen, descLen, req.getId());

        if (!enabled) {
            log.warn("[BookSensitiveWordFilter] 敏感词匹配器未启用（字典为空或 novel.ai.sensitive-filter.enabled=false），跳过本地拦截");
            return StepResult.CONTINUE;
        }
        List<String> hits = new ArrayList<>(matcher.findHitsUpTo(req.getBookName(), MAX_HITS_IN_REASON));
        if (hits.isEmpty()) {
            hits.addAll(matcher.findHitsUpTo(req.getBookDesc(), MAX_HITS_IN_REASON));
        }

        if (hits.isEmpty()) {
            return StepResult.CONTINUE;
        }

        String reason = buildReason(hits);
        log.info("[BookSensitiveWordFilter] 命中敏感词，直接拒审 bookId={} hits={}", req.getId(), hits);
        BookAuditRespDto resp = BookAuditRespDto.builder()
                .id(req.getId())
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
