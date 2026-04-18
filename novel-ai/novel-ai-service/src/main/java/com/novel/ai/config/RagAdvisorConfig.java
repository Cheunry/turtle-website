package com.novel.ai.config;

import com.novel.ai.rag.AuditExperienceDocumentRetriever;
import com.novel.ai.rag.NovelAiRagProperties;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 审核经验 RAG 的 Spring AI 原生装配。
 * <p>
 * 本配置只在 {@code novel.ai.rag.enabled=true}（默认）时生效，装配：
 * <ol>
 *     <li>{@link AuditExperienceDocumentRetriever}——对接本模块自管的 {@link VectorStore}，
 *         替换掉原先通过 Feign 调 novel-search-service 的 {@code SimilarAuditExperienceService}，
 *         ai 模块不再反向依赖任何业务模块；</li>
 *     <li>{@link RetrievalAugmentationAdvisor}——挂在 ChatClient 调用链上，
 *         在 LLM 请求发出前把召回的判例按自定义中文模板注入 user prompt，
 *         命中为空时走 emptyContextPromptTemplate（原样透传），不插入任何噪音提示。</li>
 * </ol>
 * <p>
 * <b>为什么选 {@code RetrievalAugmentationAdvisor} 而非 {@code QuestionAnswerAdvisor}</b>：
 * QA advisor 默认会把整段 user message 包进固定的英文指令模板（"Given the context...
 * you don't know"），对审核这类"结构化输出 + 中文指令"场景，模板会"抢走"原 system prompt 的语义锚点。
 * RA advisor 走 {@link ContextualQueryAugmenter}，自定义 PromptTemplate 更自然，
 * 可以保留我们原有的审核指令风格。
 * <p>
 * <b>默认不挂到 ChatClient 全局链</b>：polish / cover prompt / audit rule extract
 * 这些场景不需要 RAG，所以只在 {@code BookLlmInvokeStep} 与 {@code ChapterSegmentAuditStep}
 * 发起调用时以 {@code .advisors(ragAdvisor)} 的形式局部挂载——RAG 的副作用范围严格可控。
 */
@Configuration
@EnableConfigurationProperties(NovelAiRagProperties.class)
@ConditionalOnProperty(prefix = "novel.ai.rag", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RagAdvisorConfig {

    /**
     * 带判例的 user prompt 模板。
     * <ul>
     *     <li>{@code {query}}：原 user message 全文（已由 {@code NovelAiPromptLoader} 渲染好的审核指令）；</li>
     *     <li>{@code {context}}：{@link ContextualQueryAugmenter#DEFAULT_DOCUMENT_FORMATTER}
     *         把 {@code List<Document>} 拼接成的判例文本。</li>
     * </ul>
     * 保留中文分割符 {@code ---参考开始--- / ---参考结束---}，与旧版本 {@code similarExperiences}
     * 占位的人工可读风格一致，方便观察 prompt diff。
     */
    private static final String RAG_USER_PROMPT_TEMPLATE = """
            {query}

            ## 历史相似判例参考（系统自动召回，可为空）
            ---参考开始---
            {context}
            ---参考结束---

            请结合上述判例与【审核标准】综合判断，严格按系统 Prompt 要求的 JSON 结构输出最终决定。
            """;

    /**
     * 向量库未命中时的模板——直接原样透传 user prompt，不插入任何英文 "you don't know" 指令。
     */
    private static final String RAG_EMPTY_CONTEXT_TEMPLATE = "{query}\n";

    @Bean
    public AuditExperienceDocumentRetriever auditExperienceDocumentRetriever(
            VectorStore vectorStore, NovelAiRagProperties properties) {
        return new AuditExperienceDocumentRetriever(vectorStore, properties);
    }

    @Bean
    public RetrievalAugmentationAdvisor auditExperienceRagAdvisor(
            AuditExperienceDocumentRetriever retriever) {
        ContextualQueryAugmenter augmenter = ContextualQueryAugmenter.builder()
                .promptTemplate(new PromptTemplate(RAG_USER_PROMPT_TEMPLATE))
                .emptyContextPromptTemplate(new PromptTemplate(RAG_EMPTY_CONTEXT_TEMPLATE))
                // allowEmptyContext=true：空召回时走 emptyContextPromptTemplate 原样透传，
                // 而不是默认的 "answer: I don't know"——因为本场景不是问答，是审核。
                .allowEmptyContext(true)
                .build();
        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(retriever)
                .queryAugmenter(augmenter)
                .build();
    }
}
