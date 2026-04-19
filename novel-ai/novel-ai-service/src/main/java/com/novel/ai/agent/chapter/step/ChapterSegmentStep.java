package com.novel.ai.agent.chapter.step;

import com.novel.ai.agent.chapter.ChapterAuditContext;
import com.novel.ai.agent.chapter.support.ChapterContentSplitter;
import com.novel.ai.agent.core.AuditStep;
import com.novel.ai.agent.core.StepResult;
import com.novel.ai.config.NovelAiLearningAuditProperties;
import com.novel.book.dto.req.ChapterAuditReqDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.toolkit.trace.ActiveSpan;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 把章节内容切成若干段放入上下文。短章节只切出 1 段，保证下游 Step 无需区分长短。
 * <p>
 * 小说类默认每段 {@code novel.ai.chapter.max-content-length}（默认 5000）；学习资料类使用
 * {@code novel.ai.learning-audit.segment-chars}（或绿色通道 {@code green-channel-segment-chars}），
 * 且可配置绿色通道仅送审前 N 字以控制 Token。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChapterSegmentStep implements AuditStep<ChapterAuditContext> {

    private final ChapterContentSplitter splitter;
    private final NovelAiLearningAuditProperties learningAuditProperties;

    @Value("${novel.ai.chapter.max-content-length:5000}")
    private int maxContentLength;

    @Override
    public StepResult execute(ChapterAuditContext ctx) {
        ChapterAuditReqDto req = ctx.getRequest();
        String content = req.getContent();

        if (learningAuditProperties.matchesLearningCategory(req.getCategoryId(), req.getCategoryName())) {
            if (learningAuditProperties.isGreenChannel(req.getAuthorId())
                    && learningAuditProperties.getGreenChannelMaxAuditChars() > 0
                    && content != null
                    && content.length() > learningAuditProperties.getGreenChannelMaxAuditChars()) {
                int max = learningAuditProperties.getGreenChannelMaxAuditChars();
                req.setContent(content.substring(0, max));
                content = req.getContent();
                ctx.setLearningAuditNote(
                        "【绿色通道】正文仅送审前 " + max + " 字以控制成本，后续内容未进入模型；全量合规请结合人审或调大配置。");
                log.warn("[ChapterSegmentStep] learning green channel truncated bookId={} chapterNum={} maxChars={}",
                        req.getBookId(), req.getChapterNum(), max);
            }
            int segLen = learningAuditProperties.isGreenChannel(req.getAuthorId())
                    ? learningAuditProperties.getGreenChannelSegmentChars()
                    : learningAuditProperties.getSegmentChars();
            ctx.setSegments(splitter.split(content, segLen));
            ActiveSpan.tag("learning.audit.segmentChars", String.valueOf(segLen));
        } else {
            ctx.setSegments(splitter.split(content, maxContentLength));
        }

        if (ctx.getSegments().size() > 1) {
            ActiveSpan.tag("segments.count", String.valueOf(ctx.getSegments().size()));
        }
        return StepResult.CONTINUE;
    }
}
