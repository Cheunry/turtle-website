package com.novel.ai.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.Collections;
import java.util.List;

/**
 * 审核经验 RAG 判例库的"读取端"——Spring AI 原生 {@link DocumentRetriever} 实现。
 * <p>
 * 设计要点：
 * <ol>
 *     <li><b>Spring AI 一级抽象</b>：实现 {@link DocumentRetriever} 后可以直接挂到
 *         {@code RetrievalAugmentationAdvisor}，不再需要业务侧手写"拼 prompt 上下文"的胶水；</li>
 *     <li><b>本地向量库</b>：直接注入 {@link VectorStore}（配置里已绑定 novel-ai-service
 *         专属的 {@code novel_ai_audit_experiences} ES 索引），彻底断开对 novel-search-service
 *         的 Feign 反向依赖；</li>
 *     <li><b>失败降级</b>：检索过程中任何 {@link RuntimeException}（ES 瞬断 / embedding 超时 / 网络异常）
 *         都会被吞掉并返回 {@link Collections#emptyList()}——RAG 只是 prompt 的增强信号，
 *         "召不到"绝不能把主审核流程带挂；</li>
 *     <li><b>参数可调</b>：{@link NovelAiRagProperties#getTopK()} 和
 *         {@link NovelAiRagProperties#getSimilarityThreshold()} 从配置读，便于在灰度期动态调优。</li>
 * </ol>
 * <p>
 * 未实现的（留给后续阶段扩展）：
 * <ul>
 *     <li>按 {@code source_type=book/chapter} 做 {@code FilterExpression} 过滤，精确召回同维度判例；</li>
 *     <li>按 {@code created_at_ms} 做时间衰减，让近期人审样例权重更高；</li>
 *     <li>对召回结果去重 / 限制同一标签上限，避免 prompt 被同类判例堆满。</li>
 * </ul>
 */
@Slf4j
public class AuditExperienceDocumentRetriever implements DocumentRetriever {

    private final VectorStoreDocumentRetriever delegate;

    public AuditExperienceDocumentRetriever(VectorStore vectorStore, NovelAiRagProperties properties) {
        this.delegate = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .topK(properties.getTopK())
                .similarityThreshold(properties.getSimilarityThreshold())
                .build();
    }

    @Override
    public List<Document> retrieve(Query query) {
        if (query == null || query.text() == null || query.text().isBlank()) {
            return Collections.emptyList();
        }
        try {
            List<Document> docs = delegate.retrieve(query);
            if (log.isDebugEnabled()) {
                log.debug("[AuditExperienceRetriever] query.len={}, hits={}",
                        query.text().length(), docs == null ? 0 : docs.size());
            }
            return docs == null ? Collections.emptyList() : docs;
        } catch (RuntimeException e) {
            log.warn("[AuditExperienceRetriever] 判例向量检索失败，降级为空召回: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
