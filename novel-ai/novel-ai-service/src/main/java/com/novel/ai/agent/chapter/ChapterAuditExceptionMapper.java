package com.novel.ai.agent.chapter;

import com.novel.ai.agent.core.AbstractAuditExceptionMapper;
import com.novel.ai.agent.core.AuditErrorClassifier;
import com.novel.book.dto.req.ChapterAuditReqDto;
import com.novel.book.dto.resp.ChapterAuditRespDto;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 章节审核流水线的异常翻译器。内容安全拦截通常已在
 * {@link com.novel.ai.agent.chapter.step.ChapterSegmentAuditStep} 里被短路；
 * 本映射器主要处理"意外异常"（例如 Feign 调用失败、LLM 超时但不是内容安全拦截）。
 */
@Component
public class ChapterAuditExceptionMapper extends AbstractAuditExceptionMapper<ChapterAuditContext> {

    public ChapterAuditExceptionMapper(AuditErrorClassifier classifier) {
        super(classifier);
    }

    @Override
    protected Object buildInspectionRejectedResult(ChapterAuditContext ctx) {
        ChapterAuditReqDto req = ctx.getRequest();
        return ChapterAuditRespDto.builder()
                .bookId(req != null ? req.getBookId() : null)
                .chapterNum(req != null ? req.getChapterNum() : null)
                .auditStatus(2)
                .aiConfidence(new BigDecimal("1.0"))
                .auditReason("内容包含不当信息，不符合平台规范")
                .build();
    }

    @Override
    protected Object buildFallbackResult(ChapterAuditContext ctx, Exception error) {
        ChapterAuditReqDto req = ctx.getRequest();
        return ChapterAuditRespDto.builder()
                .bookId(req != null ? req.getBookId() : null)
                .chapterNum(req != null ? req.getChapterNum() : null)
                .auditStatus(0)
                .aiConfidence(new BigDecimal("0.0"))
                .auditReason("AI审核服务异常")
                .build();
    }
}
