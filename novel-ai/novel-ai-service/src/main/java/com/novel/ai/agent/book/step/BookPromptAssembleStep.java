package com.novel.ai.agent.book.step;

import com.novel.ai.agent.book.BookAuditContext;
import com.novel.ai.agent.core.AuditStep;
import com.novel.ai.agent.core.StepResult;
import com.novel.ai.agent.support.AuditCategoryPromptResolver;
import com.novel.ai.agent.support.PromptVars;
import com.novel.ai.config.NovelAiLearningAuditProperties;
import com.novel.ai.model.AuditDecisionAiOutput;
import com.novel.ai.prompt.NovelAiPromptKey;
import com.novel.ai.prompt.NovelAiPromptLoader;
import com.novel.book.dto.req.BookAuditReqDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 渲染 book-audit 的 system / user prompt。system 末尾附上 JSON Schema
 * 格式说明，帮助 LLM 稳定产出结构化结果。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookPromptAssembleStep implements AuditStep<BookAuditContext> {

    private final NovelAiPromptLoader promptLoader;
    private final AuditCategoryPromptResolver categoryPromptResolver;
    private final NovelAiLearningAuditProperties learningAuditProperties;

    /** 复用一份 Converter，避免每次构造重复生成 JsonSchema。 */
    private final BeanOutputConverter<AuditDecisionAiOutput> converter =
            new BeanOutputConverter<>(AuditDecisionAiOutput.class);

    @Override
    public StepResult execute(BookAuditContext ctx) {
        BookAuditReqDto req = ctx.getRequest();

        boolean learning = learningAuditProperties.matchesLearningCategory(
                req.getCategoryId(), req.getCategoryName());
        log.info("[BookPromptAssemble] bookId={} categoryId={} categoryName={} branch={}",
                req.getId(), req.getCategoryId(), req.getCategoryName(), learning ? "learning" : "novel");
        NovelAiPromptKey bookKey = learning ? NovelAiPromptKey.BOOK_AUDIT_LEARNING : NovelAiPromptKey.BOOK_AUDIT;
        String systemPrompt = promptLoader.renderSystem(bookKey)
                + "\n\n" + converter.getFormat();
        if (!learning) {
            String categoryExtra = categoryPromptResolver.resolveSystemExtra(req);
            if (!categoryExtra.isEmpty()) {
                systemPrompt += "\n\n# Category-Specific Audit Guidelines（作品类别附加规则）\n" + categoryExtra;
            }
        }

        Map<String, Object> vars = new HashMap<>();
        vars.put("bookName", PromptVars.safe(req.getBookName()));
        vars.put("bookDesc", PromptVars.safe(req.getBookDesc()));
        vars.put("categoryId", req.getCategoryId() == null ? "" : String.valueOf(req.getCategoryId()));
        vars.put("categoryName", PromptVars.safe(req.getCategoryName()));
        String userPrompt = promptLoader.renderUser(bookKey, vars);

        ctx.setSystemPrompt(systemPrompt);
        ctx.setUserPrompt(userPrompt);
        return StepResult.CONTINUE;
    }

    public BeanOutputConverter<AuditDecisionAiOutput> converter() {
        return converter;
    }
}
