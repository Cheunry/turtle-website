package com.novel.ai.agent.book.step;

import com.novel.ai.agent.book.BookAuditContext;
import com.novel.ai.agent.core.AuditStep;
import com.novel.ai.agent.core.StepResult;
import com.novel.ai.agent.support.PromptVars;
import com.novel.ai.agent.support.SimilarAuditExperienceService;
import com.novel.book.dto.req.BookAuditReqDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 用"书名 + 简介"拼接作为检索文本，查出 Top-K 相似判例作为 RAG 上下文。
 * 检索失败不影响主流程，会被 service 内部 catch 并返回空串。
 */
@Component
@RequiredArgsConstructor
public class BookRagRetrieveStep implements AuditStep<BookAuditContext> {

    private final SimilarAuditExperienceService similarService;

    @Override
    public StepResult execute(BookAuditContext ctx) {
        BookAuditReqDto req = ctx.getRequest();
        String contentToSearch = PromptVars.safe(req.getBookName()) + " " + PromptVars.safe(req.getBookDesc());
        ctx.setSimilarExperiences(similarService.retrieve(contentToSearch));
        return StepResult.CONTINUE;
    }
}
