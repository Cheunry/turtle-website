package com.novel.ai.agent.book.step;

import com.novel.ai.agent.book.BookAuditContext;
import com.novel.ai.agent.core.AuditStep;
import com.novel.ai.agent.core.StepResult;
import com.novel.ai.agent.support.PromptVars;
import com.novel.ai.model.AuditDecisionAiOutput;
import com.novel.ai.prompt.NovelAiPromptKey;
import com.novel.ai.prompt.NovelAiPromptLoader;
import com.novel.book.dto.req.BookAuditReqDto;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 渲染 book-audit 的 system / user prompt。system 末尾附上 JSON Schema
 * 格式说明，帮助 LLM 稳定产出结构化结果。
 */
@Component
@RequiredArgsConstructor
public class BookPromptAssembleStep implements AuditStep<BookAuditContext> {

    private final NovelAiPromptLoader promptLoader;

    /** 复用一份 Converter，避免每次构造重复生成 JsonSchema。 */
    private final BeanOutputConverter<AuditDecisionAiOutput> converter =
            new BeanOutputConverter<>(AuditDecisionAiOutput.class);

    @Override
    public StepResult execute(BookAuditContext ctx) {
        BookAuditReqDto req = ctx.getRequest();

        String systemPrompt = promptLoader.renderSystem(NovelAiPromptKey.BOOK_AUDIT)
                + "\n\n" + converter.getFormat();

        Map<String, Object> vars = new HashMap<>();
        vars.put("bookName", PromptVars.safe(req.getBookName()));
        vars.put("bookDesc", PromptVars.safe(req.getBookDesc()));
        vars.put("similarExperiences", PromptVars.safe(ctx.getSimilarExperiences()));
        String userPrompt = promptLoader.renderUser(NovelAiPromptKey.BOOK_AUDIT, vars);

        ctx.setSystemPrompt(systemPrompt);
        ctx.setUserPrompt(userPrompt);
        return StepResult.CONTINUE;
    }

    public BeanOutputConverter<AuditDecisionAiOutput> converter() {
        return converter;
    }
}
