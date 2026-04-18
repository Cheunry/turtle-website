package com.novel.ai.agent.chapter.step;

import com.novel.ai.agent.chapter.ChapterAuditContext;
import com.novel.ai.agent.chapter.support.ChapterContentSplitter;
import com.novel.ai.agent.core.AuditStep;
import com.novel.ai.agent.core.StepResult;
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.apm.toolkit.trace.ActiveSpan;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 把章节内容切成若干段放入上下文。短章节只切出 1 段，保证下游 Step 无需区分长短。
 * 切分阈值默认 5000 字符，可通过 {@code novel.ai.chapter.max-content-length} 覆盖。
 */
@Component
@RequiredArgsConstructor
public class ChapterSegmentStep implements AuditStep<ChapterAuditContext> {

    private final ChapterContentSplitter splitter;

    @Value("${novel.ai.chapter.max-content-length:5000}")
    private int maxContentLength;

    @Override
    public StepResult execute(ChapterAuditContext ctx) {
        String content = ctx.getRequest().getContent();
        ctx.setSegments(splitter.split(content, maxContentLength));
        if (ctx.getSegments().size() > 1) {
            ActiveSpan.tag("segments.count", String.valueOf(ctx.getSegments().size()));
        }
        return StepResult.CONTINUE;
    }
}
