package com.novel.ai.agent.chapter.step;

import com.novel.ai.agent.chapter.ChapterAuditContext;
import com.novel.ai.agent.core.AuditErrorClassifier;
import com.novel.ai.agent.core.AuditStep;
import com.novel.ai.agent.core.StepResult;
import com.novel.ai.agent.support.AuditDecisionResolver;
import com.novel.ai.agent.support.PromptVars;
import com.novel.ai.agent.support.SimilarAuditExperienceService;
import com.novel.ai.invoker.StructuredOutputInvoker;
import com.novel.ai.model.AuditDecisionAiOutput;
import com.novel.ai.prompt.NovelAiPromptKey;
import com.novel.ai.prompt.NovelAiPromptLoader;
import com.novel.book.dto.req.ChapterAuditReqDto;
import com.novel.book.dto.resp.ChapterAuditRespDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.toolkit.trace.ActiveSpan;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 对 {@link ChapterAuditContext#getSegments()} 中的每一段依次做"RAG + Prompt + LLM 调用"。
 *
 * <p>异常策略沿用原实现：</p>
 * <ul>
 *     <li>某段触发内容安全拦截 → 整章立即短路为"审核不通过"；</li>
 *     <li>某段其他异常 → 该段记为"待审核"，继续审核下一段。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChapterSegmentAuditStep implements AuditStep<ChapterAuditContext> {

    private final ChatClient chatClient;
    private final NovelAiPromptLoader promptLoader;
    private final StructuredOutputInvoker invoker;
    private final SimilarAuditExperienceService similarService;
    private final AuditDecisionResolver resolver;
    private final AuditErrorClassifier errorClassifier;

    private final BeanOutputConverter<AuditDecisionAiOutput> converter =
            new BeanOutputConverter<>(AuditDecisionAiOutput.class);

    @Override
    public StepResult execute(ChapterAuditContext ctx) {
        ChapterAuditReqDto req = ctx.getRequest();
        List<String> segments = ctx.getSegments();
        boolean isMultiSegment = segments.size() > 1;

        for (int i = 0; i < segments.size(); i++) {
            String segment = segments.get(i);
            int index = i + 1;
            String logContext = isMultiSegment ? "chapter-audit-seg-" + index : "chapter-audit";

            String similar = similarService.retrieve(segment);
            String systemPrompt = promptLoader.renderSystem(NovelAiPromptKey.CHAPTER_AUDIT)
                    + "\n\n" + converter.getFormat();
            String userPrompt = promptLoader.renderUser(
                    NovelAiPromptKey.CHAPTER_AUDIT,
                    buildUserVars(req.getChapterName(), segment, index, segments.size(), similar));

            long segmentStart = System.currentTimeMillis();
            try {
                AuditDecisionAiOutput aiOutput = invoker.invoke(
                        chatClient, systemPrompt, userPrompt, converter, logContext);
                long duration = System.currentTimeMillis() - segmentStart;
                if (isMultiSegment) {
                    ActiveSpan.tag("segment." + index + ".duration.ms", String.valueOf(duration));
                }
                log.info("AI审核响应，章节 bookId: {}, chapterNum: {}, 第 {}/{} 段, 耗时: {}ms, aiOutput: {}",
                        req.getBookId(), req.getChapterNum(), index, segments.size(), duration, aiOutput);

                ctx.getSegmentResults().add(buildSegmentResp(aiOutput, req));
            } catch (Exception e) {
                if (errorClassifier.isContentInspectionFailed(e)) {
                    // 任一段命中安全拦截即整章不通过
                    if (isMultiSegment) {
                        ActiveSpan.tag("segment." + index + ".error.category", "content_inspection_failed");
                    }
                    log.warn("第 {}/{} 段命中内容安全拦截，章节 bookId: {}, chapterNum: {}, 整章标记为审核不通过",
                            index, segments.size(), req.getBookId(), req.getChapterNum());
                    ctx.setResult(ChapterAuditRespDto.builder()
                            .bookId(req.getBookId())
                            .chapterNum(req.getChapterNum())
                            .auditStatus(2)
                            .aiConfidence(new BigDecimal("1.0"))
                            .auditReason("内容包含不当信息，不符合平台规范")
                            .build());
                    return StepResult.SHORT_CIRCUIT;
                }
                log.error("AI审核异常，章节 bookId: {}, chapterNum: {}, 第 {}/{} 段",
                        req.getBookId(), req.getChapterNum(), index, segments.size(), e);
                // 其他异常：当前段降级为"待审核"，继续下一段
                ctx.getSegmentResults().add(ChapterAuditRespDto.builder()
                        .bookId(req.getBookId())
                        .chapterNum(req.getChapterNum())
                        .auditStatus(0)
                        .aiConfidence(new BigDecimal("0.0"))
                        .auditReason(isMultiSegment
                                ? "第" + index + "段审核异常"
                                : "AI审核服务异常")
                        .build());
            }
        }
        return StepResult.CONTINUE;
    }

    private Map<String, Object> buildUserVars(String chapterName, String content,
                                              int segmentIndex, int totalSegments,
                                              String similarExperiences) {
        String segmentInfo = totalSegments > 1
                ? String.format("这是章节内容的第 %d/%d 段，请对该段内容进行审核。", segmentIndex, totalSegments)
                : "（未分段）";
        Map<String, Object> vars = new HashMap<>();
        vars.put("segmentInfo", segmentInfo);
        vars.put("chapterName", PromptVars.safe(chapterName));
        vars.put("chapterContent", PromptVars.safe(content));
        vars.put("similarExperiences", PromptVars.safe(similarExperiences));
        return vars;
    }

    private ChapterAuditRespDto buildSegmentResp(AuditDecisionAiOutput aiOutput, ChapterAuditReqDto req) {
        return ChapterAuditRespDto.builder()
                .bookId(req.getBookId())
                .chapterNum(req.getChapterNum())
                .auditStatus(resolver.resolveStatus(aiOutput))
                .aiConfidence(resolver.resolveConfidence(aiOutput))
                .auditReason(resolver.resolveReason(aiOutput))
                .build();
    }
}
