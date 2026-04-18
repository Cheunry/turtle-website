package com.novel.ai.tool;

import com.novel.ai.dto.req.AuditExperienceUpsertReqDto;
import com.novel.ai.mq.AiMqConsts;
import com.novel.ai.mq.HumanReviewTaskProducer;
import com.novel.ai.mq.dto.HumanReviewTaskMqDto;
import com.novel.ai.rag.AuditExperienceIndexer;
import com.novel.ai.rag.AuditExperienceMetadata;
import com.novel.ai.sensitive.SensitiveWordMatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Novel AI Agent 的工具箱。每个 {@link Tool} 方法都是大模型可自主调用的"外部能力"——
 * 当模型在审核/润色过程中决定需要查外部信息或触发外部动作时，会通过 Spring AI 的
 * Function Calling 协议自动调用本类方法。
 * <p>
 * <b>设计原则</b>：
 * <ol>
 *     <li><b>真实可用</b>：每个 Tool 都接入真实数据源（Nacos / VectorStore / MQ / 本地字典），
 *         不做"Mock 样例"装饰，否则会在 MCP demo 中穿帮；</li>
 *     <li><b>防御式输出</b>：失败路径返回可读的空结果或错误描述，<b>不抛异常</b>——
 *         异常会中断模型的 ReAct 循环，反而让审核质量变差；</li>
 *     <li><b>参数 JSON Schema 自动生成</b>：{@link ToolParam#description()} 会被 Spring AI
 *         填入 OpenAI/DashScope Function Schema 的 description，直接引导模型正确填参；</li>
 *     <li><b>与 MCP 共用</b>：本类由 {@link com.novel.ai.config.McpToolRegistrationConfig}
 *         注册为 {@link org.springframework.ai.tool.ToolCallbackProvider}，供 MCP Server 暴露；
 *         一套代码同时服务"内部 Agent 调用"和"Cursor/Claude Desktop 远程调用"。</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditTools {

    private final NovelAiPolicyProperties policyProperties;
    private final VectorStore vectorStore;
    private final AuditExperienceIndexer auditExperienceIndexer;
    private final SensitiveWordMatcher sensitiveWordMatcher;
    private final HumanReviewTaskProducer humanReviewTaskProducer;

    // ===================================================================
    // Tool #1 ：政策查询
    // ===================================================================

    @Tool(description = """
            查询平台对某类主题的审核政策条款。
            当你在审核过程中需要"平台明文规定"作为判定依据时调用本工具。
            输入的 topic 必须是下列之一：violence / porn / politics / religion / ad / privacy。
            返回命中的政策条目；若无命中则返回空列表——此时应按常识判断并标注 confidence 偏低。
            """)
    public PolicyQueryResult queryPlatformPolicy(
            @ToolParam(description = "主题分类，小写英文：violence / porn / politics / religion / ad / privacy")
            String topic) {
        List<NovelAiPolicyProperties.PolicyEntry> rules = policyProperties.findByTopic(topic);
        log.info("[Tool:queryPlatformPolicy] topic={} hits={}", topic, rules.size());
        return new PolicyQueryResult(topic, rules);
    }

    // ===================================================================
    // Tool #2 ：向量检索相似判例
    // ===================================================================

    @Tool(description = """
            在历史判例库中检索语义相似的违规案例，用于辅助判断"类似文本以往是如何定性的"。
            输入一段你正在审核的文本（可以是片段），返回 Top-K 相似判例，
            每条包含：违规标签、审核规则、原文片段、置信度。
            若传入 violationLabel 非空，则进一步限定在该标签下检索；否则跨标签检索。
            """)
    public SimilarViolationsResult querySimilarViolations(
            @ToolParam(description = "待检索的文本片段，一般是当前被审核的段落")
            String queryText,
            @ToolParam(description = "可选：违规标签过滤，如 violence/porn/politics；不确定时传空串")
            String violationLabel,
            @ToolParam(description = "召回条数，建议 3~5，超过 10 会被截断")
            Integer topK) {
        if (queryText == null || queryText.isBlank()) {
            return SimilarViolationsResult.empty("queryText 不能为空");
        }
        int k = topK == null || topK <= 0 ? 3 : Math.min(topK, 10);
        try {
            SearchRequest.Builder builder = SearchRequest.builder()
                    .query(queryText)
                    .topK(k)
                    .similarityThreshold(0.65);
            if (violationLabel != null && !violationLabel.isBlank()) {
                builder.filterExpression(
                        AuditExperienceMetadata.VIOLATION_LABEL + " == '" + violationLabel.trim() + "'");
            }
            List<Document> docs = vectorStore.similaritySearch(builder.build());
            List<SimilarCase> cases = new ArrayList<>();
            if (docs != null) {
                for (Document d : docs) {
                    cases.add(toSimilarCase(d));
                }
            }
            log.info("[Tool:querySimilarViolations] queryLen={} label={} topK={} hits={}",
                    queryText.length(), violationLabel, k, cases.size());
            return new SimilarViolationsResult(cases, null);
        } catch (RuntimeException e) {
            log.warn("[Tool:querySimilarViolations] 向量检索失败，返回空: {}", e.getMessage());
            return SimilarViolationsResult.empty("向量库检索失败：" + e.getMessage());
        }
    }

    // ===================================================================
    // Tool #3 ：按书籍维度查历史判例
    // ===================================================================

    @Tool(description = """
            查询某本书历史上沉淀过的审核判例（按时间倒序）。
            当你怀疑一本书"多次违规/惯犯"时调用本工具。
            返回的判例可用来：判断作者书写风格是否反复触雷 / 是否可以直接给累犯判决。
            """)
    public RecentDecisionsResult queryRecentAuditDecisionsForBook(
            @ToolParam(description = "书籍 ID") Long bookId,
            @ToolParam(description = "最多返回条数，上限 10") Integer limit) {
        if (bookId == null) {
            return RecentDecisionsResult.empty("bookId 不能为空");
        }
        int k = limit == null || limit <= 0 ? 5 : Math.min(limit, 10);
        try {
            // 本工具用"书籍判例通用画像"这串固定文本作为语义查询种子，
            // 真实过滤交给 filterExpression 完成；在 embedding 相似度无明显意义的场景下
            // 改用一次无文本过滤的 exists 查询。
            SearchRequest req = SearchRequest.builder()
                    .query("历史判例")
                    .topK(k)
                    .similarityThreshold(0.0)
                    .filterExpression(AuditExperienceMetadata.BOOK_ID + " == " + bookId)
                    .build();
            List<Document> docs = vectorStore.similaritySearch(req);
            List<SimilarCase> cases = new ArrayList<>();
            if (docs != null) {
                for (Document d : docs) {
                    cases.add(toSimilarCase(d));
                }
            }
            log.info("[Tool:queryRecentAuditDecisionsForBook] bookId={} hits={}", bookId, cases.size());
            return new RecentDecisionsResult(bookId, cases, null);
        } catch (RuntimeException e) {
            log.warn("[Tool:queryRecentAuditDecisionsForBook] bookId={} 查询失败: {}", bookId, e.getMessage());
            return RecentDecisionsResult.empty("向量库查询失败：" + e.getMessage());
        }
    }

    // ===================================================================
    // Tool #4 ：敏感词扫描
    // ===================================================================

    @Tool(description = """
            对一段文本执行本地敏感词字典扫描（Aho-Corasick 多模式匹配，O(n) 一次扫描）。
            当你想在作出审核判定前再"兜底"一次机械扫描时调用本工具——
            命中项通常意味着"硬违规"，应直接判 auditStatus=2（不通过）。
            """)
    public SensitiveWordsResult checkSensitiveWords(
            @ToolParam(description = "被扫描的文本") String text) {
        if (text == null || text.isBlank()) {
            return new SensitiveWordsResult(false, Collections.emptyList(), "text 为空，跳过扫描");
        }
        if (!sensitiveWordMatcher.isEnabled()) {
            return new SensitiveWordsResult(false, Collections.emptyList(), "敏感词字典未启用");
        }
        List<String> hits = sensitiveWordMatcher.findAll(text);
        log.info("[Tool:checkSensitiveWords] textLen={} hits={}", text.length(), hits.size());
        return new SensitiveWordsResult(!hits.isEmpty(), hits, null);
    }

    // ===================================================================
    // Tool #5 ：升级为人工审核
    // ===================================================================

    @Tool(description = """
            当你无法做出高置信度判定（如置信度 < 0.6），或者命中高严重度政策但证据不足时，
            调用本工具把案例升级到"人工审核队列"。本工具异步投递 MQ，不会阻塞审核主流程。
            suggestedAction 取值：reject / pending / pass-with-warning；
            sourceType 取值：book / chapter / agent_tool（对应 AgentTool 直接场景）。
            """)
    public EscalateResult escalateToHuman(
            @ToolParam(description = "来源类型：book / chapter / agent_tool") String sourceType,
            @ToolParam(description = "来源主键：书籍 ID 或章节 ID，可为空") Long sourceId,
            @ToolParam(description = "所属书籍 ID（章节场景需要）") Long bookId,
            @ToolParam(description = "升级原因，一句话摘要（<=200 字）") String reason,
            @ToolParam(description = "疑似违规片段原文（<=300 字），无则传空串") String keySnippet,
            @ToolParam(description = "AI 初判：0 待定 / 1 通过 / 2 不通过") Integer aiAuditStatus,
            @ToolParam(description = "AI 置信度 0.0~1.0") Double aiConfidence,
            @ToolParam(description = "建议处置：reject / pending / pass-with-warning") String suggestedAction) {
        String tag = resolveEscalateTag(sourceType);
        HumanReviewTaskMqDto task = HumanReviewTaskMqDto.builder()
                .sourceType(sourceType == null ? "agent_tool" : sourceType)
                .sourceId(sourceId)
                .bookId(bookId)
                .reason(truncate(reason, 200))
                .keySnippet(truncate(keySnippet, 300))
                .aiAuditStatus(aiAuditStatus)
                .aiConfidence(aiConfidence == null ? null : BigDecimal.valueOf(aiConfidence))
                .suggestedAction(suggestedAction)
                .build();
        String taskId = humanReviewTaskProducer.send(task, tag);
        return new EscalateResult(taskId, "人审工单已投递，等待人工处理");
    }

    // ===================================================================
    // Tool #6 ：审核经验回写向量库（RAG 飞轮）
    // ===================================================================

    @Tool(description = """
            把当前这次审核得出的判例沉淀到向量库，用于未来类似场景的 RAG 召回。
            仅当 violationLabel 非空（即有明确违规类型）时调用本工具，否则会被跳过。
            本工具完成了"审核越多 → 判例库越丰富 → 模型越准"的飞轮闭环。
            """)
    public RecordExperienceResult recordAuditExperience(
            @ToolParam(description = "审核唯一 ID，用作幂等键") Long auditId,
            @ToolParam(description = "来源类型：book / chapter / chapter_segment") String sourceType,
            @ToolParam(description = "来源主键：书籍 ID 或章节 ID") Long sourceId,
            @ToolParam(description = "审核结果：0 待定 / 1 通过 / 2 不通过") Integer auditStatus,
            @ToolParam(description = "违规标签，如 violence / porn；为空不入库") String violationLabel,
            @ToolParam(description = "判例规则总结，可直接复用为未来 prompt 片段") String auditRule,
            @ToolParam(description = "核心争议原文片段（<=300 字）") String keySnippet,
            @ToolParam(description = "置信度 0.0~1.0") Double confidence) {
        AuditExperienceUpsertReqDto req = new AuditExperienceUpsertReqDto();
        req.setAuditId(auditId);
        req.setSourceType(sourceType);
        req.setSourceId(sourceId);
        req.setAuditStatus(auditStatus);
        req.setViolationLabel(violationLabel);
        req.setAuditRule(auditRule);
        req.setKeySnippet(truncate(keySnippet, 300));
        req.setConfidence(confidence == null ? null : BigDecimal.valueOf(confidence));
        req.setCreatedAtMs(System.currentTimeMillis());

        AuditExperienceIndexer.IndexResult result =
                auditExperienceIndexer.upsert(List.of(req), false);
        log.info("[Tool:recordAuditExperience] auditId={} label={} result={}",
                auditId, violationLabel, result);
        return new RecordExperienceResult(
                result.getAccepted() > 0,
                result.getAccepted(),
                result.getSkipped(),
                result.getFailed());
    }

    // ===================================================================
    // 内部辅助
    // ===================================================================

    private SimilarCase toSimilarCase(Document d) {
        Map<String, Object> md = d.getMetadata();
        String label = toStringSafe(md.get(AuditExperienceMetadata.VIOLATION_LABEL));
        String rule = toStringSafe(md.get(AuditExperienceMetadata.AUDIT_RULE));
        String snippet = toStringSafe(md.get(AuditExperienceMetadata.KEY_SNIPPET));
        Object status = md.get(AuditExperienceMetadata.AUDIT_STATUS);
        Object conf = md.get(AuditExperienceMetadata.CONFIDENCE);
        Object sourceType = md.get(AuditExperienceMetadata.SOURCE_TYPE);
        Double score = d.getScore();
        return new SimilarCase(
                d.getId(),
                label,
                rule,
                snippet,
                status instanceof Number n ? n.intValue() : null,
                conf instanceof Number n ? n.doubleValue() : null,
                toStringSafe(sourceType),
                score);
    }

    private String resolveEscalateTag(String sourceType) {
        if (sourceType == null) {
            return AiMqConsts.HumanReviewTaskMq.TAG_FROM_AGENT_TOOL;
        }
        return switch (sourceType.trim().toLowerCase()) {
            case "book" -> AiMqConsts.HumanReviewTaskMq.TAG_FROM_BOOK;
            case "chapter" -> AiMqConsts.HumanReviewTaskMq.TAG_FROM_CHAPTER;
            default -> AiMqConsts.HumanReviewTaskMq.TAG_FROM_AGENT_TOOL;
        };
    }

    private static String toStringSafe(Object o) {
        return o == null ? null : o.toString();
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() > max ? s.substring(0, max) : s;
    }

    // ===================================================================
    // 返回值 DTO（均为 record，对 Jackson 序列化零配置友好）
    // ===================================================================

    public record PolicyQueryResult(String topic, List<NovelAiPolicyProperties.PolicyEntry> policies) {
    }

    public record SimilarCase(
            String auditId,
            String violationLabel,
            String auditRule,
            String keySnippet,
            Integer auditStatus,
            Double confidence,
            String sourceType,
            Double similarityScore) {
    }

    public record SimilarViolationsResult(List<SimilarCase> cases, String error) {
        public static SimilarViolationsResult empty(String error) {
            return new SimilarViolationsResult(Collections.emptyList(), error);
        }
    }

    public record RecentDecisionsResult(Long bookId, List<SimilarCase> cases, String error) {
        public static RecentDecisionsResult empty(String error) {
            return new RecentDecisionsResult(null, Collections.emptyList(), error);
        }
    }

    public record SensitiveWordsResult(boolean hit, List<String> words, String note) {
    }

    public record EscalateResult(String taskId, String message) {
    }

    public record RecordExperienceResult(boolean indexed, int accepted, int skipped, int failed) {
    }
}
