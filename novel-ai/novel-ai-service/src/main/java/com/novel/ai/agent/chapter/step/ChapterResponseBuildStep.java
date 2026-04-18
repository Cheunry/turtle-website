package com.novel.ai.agent.chapter.step;

import com.novel.ai.agent.chapter.ChapterAuditContext;
import com.novel.ai.agent.chapter.support.ChapterSegmentMerger;
import com.novel.ai.agent.core.AuditStep;
import com.novel.ai.agent.core.StepResult;
import com.novel.book.dto.req.ChapterAuditReqDto;
import com.novel.book.dto.resp.ChapterAuditRespDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 将各段审核结果合并为整章的最终响应。单段直接透传，多段交给
 * {@link ChapterSegmentMerger} 做规则合并。
 */
@Component
@RequiredArgsConstructor
public class ChapterResponseBuildStep implements AuditStep<ChapterAuditContext> {

    private final ChapterSegmentMerger merger;

    @Override
    public StepResult execute(ChapterAuditContext ctx) {
        ChapterAuditReqDto req = ctx.getRequest();
        List<ChapterAuditRespDto> segmentResults = ctx.getSegmentResults();

        if (segmentResults.size() == 1) {
            // 单段直接透传，避免合并逻辑把 auditReason 改成"共 1 段审核通过"
            ChapterAuditRespDto single = segmentResults.get(0);
            ctx.setResult(ChapterAuditRespDto.builder()
                    .bookId(req.getBookId())
                    .chapterNum(req.getChapterNum())
                    .auditStatus(single.getAuditStatus())
                    .aiConfidence(single.getAiConfidence())
                    .auditReason(single.getAuditReason())
                    .build());
            return StepResult.CONTINUE;
        }

        ctx.setResult(merger.merge(segmentResults, req.getBookId(), req.getChapterNum()));
        return StepResult.CONTINUE;
    }
}
