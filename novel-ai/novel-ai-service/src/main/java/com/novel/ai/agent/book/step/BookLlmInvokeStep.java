package com.novel.ai.agent.book.step;

import com.novel.ai.agent.book.BookAuditContext;
import com.novel.ai.agent.core.AuditStep;
import com.novel.ai.agent.core.StepResult;
import com.novel.ai.config.NovelAiLearningAuditProperties;
import com.novel.ai.invoker.StructuredOutputInvoker;
import com.novel.ai.model.AuditDecisionAiOutput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 通过 {@link StructuredOutputInvoker} 调 LLM，获得 {@link AuditDecisionAiOutput}。
 * <p>
 * 调用时<b>局部挂载</b> {@link RetrievalAugmentationAdvisor}——Spring AI 1.0 的 RAG advisor
 * 会在 LLM 请求发出前，用向量检索召回判例并按配置的模板自动注入 user prompt，
 * 命中为空时走 emptyContextPromptTemplate 原样透传，不污染 prompt。
 * <p>
 * RAG advisor 作为可选依赖（{@link ObjectProvider}）：当
 * {@code novel.ai.rag.enabled=false} 时 bean 不装配，这里直接跳过，LlmStep 退化成"纯 Prompt"调用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookLlmInvokeStep implements AuditStep<BookAuditContext> {

    private final ChatClient chatClient;
    private final StructuredOutputInvoker invoker;
    private final BookPromptAssembleStep promptStep;
    private final ObjectProvider<RetrievalAugmentationAdvisor> ragAdvisorProvider;
    private final NovelAiLearningAuditProperties learningAuditProperties;

    @Override
    public StepResult execute(BookAuditContext ctx) {
        var req = ctx.getRequest();
        if (req != null && learningAuditProperties.shouldBypassLlmForLearning(
                req.getCategoryId(), req.getCategoryName(), req.getAuthorId())) {
            log.info("[BookLlmInvoke] 学习资料绿色通道免模型审核 bookId={} authorId={}",
                    req.getId(), req.getAuthorId());
            ctx.setAiOutput(new AuditDecisionAiOutput(1, 1.0, "学习资料绿色通道：免模型审核"));
            return StepResult.CONTINUE;
        }
        boolean learning = learningAuditProperties.matchesLearningCategory(
                ctx.getRequest() != null ? ctx.getRequest().getCategoryId() : null,
                ctx.getRequest() != null ? ctx.getRequest().getCategoryName() : null);
        RetrievalAugmentationAdvisor ragAdvisor = ragAdvisorProvider.getIfAvailable();
        Advisor[] extras = (learning || ragAdvisor == null) ? null : new Advisor[]{ragAdvisor};

        AuditDecisionAiOutput output = invoker.invoke(
                chatClient,
                ctx.getSystemPrompt(),
                ctx.getUserPrompt(),
                promptStep.converter(),
                learning ? "book-audit-learning" : "book-audit",
                extras);
        ctx.setAiOutput(output);
        return StepResult.CONTINUE;
    }
}
