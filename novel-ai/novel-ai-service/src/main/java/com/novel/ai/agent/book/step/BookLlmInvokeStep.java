package com.novel.ai.agent.book.step;

import com.novel.ai.agent.book.BookAuditContext;
import com.novel.ai.agent.core.AuditStep;
import com.novel.ai.agent.core.StepResult;
import com.novel.ai.invoker.StructuredOutputInvoker;
import com.novel.ai.model.AuditDecisionAiOutput;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/**
 * 通过 {@link StructuredOutputInvoker} 调 LLM，获得 {@link AuditDecisionAiOutput}。
 * 重试与"修复型 system prompt"逻辑已在 Invoker 内部实现；此处只负责装配调用。
 */
@Component
@RequiredArgsConstructor
public class BookLlmInvokeStep implements AuditStep<BookAuditContext> {

    private final ChatClient chatClient;
    private final StructuredOutputInvoker invoker;
    private final BookPromptAssembleStep promptStep;

    @Override
    public StepResult execute(BookAuditContext ctx) {
        AuditDecisionAiOutput output = invoker.invoke(
                chatClient,
                ctx.getSystemPrompt(),
                ctx.getUserPrompt(),
                promptStep.converter(),
                "book-audit");
        ctx.setAiOutput(output);
        return StepResult.CONTINUE;
    }
}
