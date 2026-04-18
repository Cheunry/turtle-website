package com.novel.ai.rag;

import com.novel.ai.dto.req.AuditExperienceUpsertReqDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 审核经验 RAG 判例库的"写入端"。
 * <p>
 * <b>模块解耦原则</b>：本类只依赖 {@link AuditExperienceUpsertReqDto}（novel-ai 模块自有 DTO），
 * 不依赖任何业务模块的 Feign 或 DTO。判例来源由调用方显式推送，ai 模块不反向拉取。
 * <p>
 * 设计原则：
 * <ol>
 *     <li><b>违规标签非空兜底</b>：{@code violationLabel} 为空直接跳过——没有标签的样例
 *         在 RAG 里无法作为"判例"使用。质量判断（是否人审过等）由推送端负责。</li>
 *     <li><b>幂等写入</b>：{@link Document#getId()} 直接用 {@code auditId.toString()}，
 *         Spring AI {@code ElasticsearchVectorStore} 底层走 ES {@code _doc} 的 index 语义（即 upsert），
 *         所以重复执行 {@link #upsert(List, boolean)} 不会产生脏数据，只会重新 embedding。</li>
 *     <li><b>写失败不阻塞</b>：Indexer 把单次 {@code vectorStore.add} 失败吞掉并计入失败计数，
 *         不抛给调用方——在线路径上入库失败绝不能影响主审核流程。</li>
 * </ol>
 * <p>
 * 向量化的 {@code content} 拼接策略：
 * <pre>
 *   违规标签: {label}\n判例规则: {rule}\n核心片段: {snippet}
 * </pre>
 * 三个字段一起 embedding，让"标签/规则/片段"都参与语义空间——相比只 embedding 片段，
 * 能把"同一类违规的不同表达"聚到一起，召回时 top-k 更稳。
 */
@Slf4j
@Component
public class AuditExperienceIndexer {

    /**
     * 每次批量写入 ES 的最大 Document 数——避免一次请求体过大，且便于失败局部降级。
     */
    private static final int DEFAULT_BATCH_SIZE = 20;

    private final VectorStore vectorStore;

    public AuditExperienceIndexer(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /**
     * 把一批审核判例写入向量库。
     *
     * @param experiences 判例列表（由调用方从业务侧组装后推送）
     * @param dryRun      {@code true} 时只构造 Document 统计合规数，不实际调用 VectorStore
     * @return 本次处理结果
     */
    public IndexResult upsert(List<AuditExperienceUpsertReqDto> experiences, boolean dryRun) {
        IndexResult result = new IndexResult();
        if (experiences == null || experiences.isEmpty()) {
            return result;
        }

        List<Document> buffer = new ArrayList<>(DEFAULT_BATCH_SIZE);
        for (AuditExperienceUpsertReqDto exp : experiences) {
            result.totalScanned++;
            Document doc = tryBuildDocument(exp);
            if (doc == null) {
                result.skipped++;
                continue;
            }
            buffer.add(doc);
            if (buffer.size() >= DEFAULT_BATCH_SIZE) {
                flush(buffer, dryRun, result);
                buffer.clear();
            }
        }
        if (!buffer.isEmpty()) {
            flush(buffer, dryRun, result);
        }
        return result;
    }

    private void flush(List<Document> buffer, boolean dryRun, IndexResult result) {
        if (dryRun) {
            result.accepted += buffer.size();
            log.info("[AuditExperienceIndexer] dryRun=true，本批拟入库 {} 条（未实际写入）", buffer.size());
            return;
        }
        try {
            vectorStore.add(buffer);
            result.accepted += buffer.size();
            log.info("[AuditExperienceIndexer] 向量库写入成功: {} 条", buffer.size());
        } catch (RuntimeException e) {
            result.failed += buffer.size();
            log.error("[AuditExperienceIndexer] 向量库批量写入失败: batch={}, error={}",
                    buffer.size(), e.getMessage(), e);
        }
    }

    /**
     * 将单条判例请求转成可入库的 {@link Document}。
     * 不满足条件时返回 {@code null}，由上层计入 skipped。
     */
    private Document tryBuildDocument(AuditExperienceUpsertReqDto exp) {
        if (exp == null || exp.getAuditId() == null) {
            log.debug("[AuditExperienceIndexer] 跳过: auditId 为空");
            return null;
        }

        String label = safeTrim(exp.getViolationLabel());
        if (label.isEmpty()) {
            log.debug("[AuditExperienceIndexer] 跳过 auditId={}: violation_label 为空，不构成判例",
                    exp.getAuditId());
            return null;
        }

        String rule = safeTrim(exp.getAuditRule());
        String snippet = safeTrim(exp.getKeySnippet());
        if (rule.isEmpty() && snippet.isEmpty()) {
            log.debug("[AuditExperienceIndexer] 跳过 auditId={}: 判例规则与核心片段均为空",
                    exp.getAuditId());
            return null;
        }

        String content = buildEmbeddingContent(label, rule, snippet);
        Map<String, Object> metadata = buildMetadata(exp, label, rule, snippet);
        return new Document(exp.getAuditId().toString(), content, metadata);
    }

    /**
     * 构造 embedding 的源文本。标签开头让"类别关键字"权重高于正文。
     */
    private String buildEmbeddingContent(String label, String rule, String snippet) {
        StringBuilder sb = new StringBuilder();
        sb.append("违规标签: ").append(label);
        if (!rule.isEmpty()) {
            sb.append('\n').append("判例规则: ").append(rule);
        }
        if (!snippet.isEmpty()) {
            sb.append('\n').append("核心片段: ").append(snippet);
        }
        return sb.toString();
    }

    private Map<String, Object> buildMetadata(AuditExperienceUpsertReqDto exp,
                                              String label, String rule, String snippet) {
        Map<String, Object> md = new HashMap<>(12);
        md.put(AuditExperienceMetadata.AUDIT_ID, exp.getAuditId());
        if (exp.getAuditStatus() != null) {
            md.put(AuditExperienceMetadata.AUDIT_STATUS, exp.getAuditStatus());
        }
        md.put(AuditExperienceMetadata.VIOLATION_LABEL, label);
        if (!rule.isEmpty()) {
            md.put(AuditExperienceMetadata.AUDIT_RULE, rule);
        }
        if (!snippet.isEmpty()) {
            md.put(AuditExperienceMetadata.KEY_SNIPPET, snippet);
        }
        BigDecimal confidence = exp.getConfidence();
        if (confidence != null) {
            md.put(AuditExperienceMetadata.CONFIDENCE, confidence.doubleValue());
        }
        md.put(AuditExperienceMetadata.CREATED_AT_MS,
                exp.getCreatedAtMs() != null ? exp.getCreatedAtMs() : System.currentTimeMillis());

        String sourceTypeTag = normalizeSourceType(exp.getSourceType());
        md.put(AuditExperienceMetadata.SOURCE_TYPE, sourceTypeTag);
        if (exp.getSourceId() != null) {
            if (AuditExperienceMetadata.SOURCE_TYPE_BOOK.equals(sourceTypeTag)) {
                md.put(AuditExperienceMetadata.BOOK_ID, exp.getSourceId());
            } else {
                md.put(AuditExperienceMetadata.CHAPTER_ID, exp.getSourceId());
            }
        }
        return md;
    }

    /**
     * 把外部传入的 sourceType（可能是自由文本）归一到枚举常量之一，
     * 非法值一律当作 book（判例索引以书籍维度为主）。
     */
    private String normalizeSourceType(String sourceType) {
        if (sourceType == null) {
            return AuditExperienceMetadata.SOURCE_TYPE_BOOK;
        }
        String t = sourceType.trim().toLowerCase();
        switch (t) {
            case AuditExperienceMetadata.SOURCE_TYPE_CHAPTER:
                return AuditExperienceMetadata.SOURCE_TYPE_CHAPTER;
            case AuditExperienceMetadata.SOURCE_TYPE_CHAPTER_SEGMENT:
                return AuditExperienceMetadata.SOURCE_TYPE_CHAPTER_SEGMENT;
            case AuditExperienceMetadata.SOURCE_TYPE_BOOK:
            default:
                return AuditExperienceMetadata.SOURCE_TYPE_BOOK;
        }
    }

    private static String safeTrim(String s) {
        return s == null ? "" : s.trim();
    }

    /**
     * 批量入库结果。字段名故意保持 snake_case 语义（{@code totalScanned/accepted/skipped/failed}），
     * 方便直接序列化给 inner endpoint 的调用方观察。
     */
    public static class IndexResult {
        public int totalScanned;
        public int accepted;
        public int skipped;
        public int failed;

        public int getTotalScanned() { return totalScanned; }
        public int getAccepted() { return accepted; }
        public int getSkipped() { return skipped; }
        public int getFailed() { return failed; }

        public IndexResult merge(IndexResult other) {
            if (other == null) {
                return this;
            }
            this.totalScanned += other.totalScanned;
            this.accepted += other.accepted;
            this.skipped += other.skipped;
            this.failed += other.failed;
            return this;
        }

        @Override
        public String toString() {
            return "IndexResult{totalScanned=" + totalScanned
                    + ", accepted=" + accepted
                    + ", skipped=" + skipped
                    + ", failed=" + failed + '}';
        }
    }
}
