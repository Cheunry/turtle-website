package com.novel.ai.agent.chapter.step;

import com.novel.ai.agent.chapter.ChapterAuditContext;
import com.novel.ai.agent.core.AuditStep;
import com.novel.ai.agent.core.StepResult;
import com.novel.book.dto.req.ChapterAuditReqDto;
import com.novel.book.dto.resp.ChapterAuditRespDto;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 章节审核的入口校验：
 * <ul>
 *     <li>请求为空 → 短路"待审核"；</li>
 *     <li>章节内容为空/空白 → 短路"章节内容为空，需要人工审核"（与原实现一致）。</li>
 * </ul>
 */
@Component
public class ChapterValidateStep implements AuditStep<ChapterAuditContext> {

    @Override
    public StepResult execute(ChapterAuditContext ctx) {
        ChapterAuditReqDto req = ctx.getRequest();
        if (req == null) {
            ctx.setResult(ChapterAuditRespDto.builder()
                    .auditStatus(0)
                    .aiConfidence(new BigDecimal("0.0"))
                    .auditReason("请求为空")
                    .build());
            return StepResult.SHORT_CIRCUIT;
        }
        String content = req.getContent();
        if (content == null || content.trim().isEmpty()) {
            ctx.setResult(ChapterAuditRespDto.builder()
                    .bookId(req.getBookId())
                    .chapterNum(req.getChapterNum())
                    .auditStatus(0)
                    .aiConfidence(new BigDecimal("0.0"))
                    .auditReason("章节内容为空，需要人工审核")
                    .build());
            return StepResult.SHORT_CIRCUIT;
        }
        return StepResult.CONTINUE;
    }
}
