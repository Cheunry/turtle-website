package com.novel.ai.agent.chapter;

import com.novel.ai.agent.chapter.step.ChapterResponseBuildStep;
import com.novel.ai.agent.chapter.step.ChapterSegmentAuditStep;
import com.novel.ai.agent.chapter.step.ChapterSegmentStep;
import com.novel.ai.agent.chapter.step.ChapterSensitiveWordFilterStep;
import com.novel.ai.agent.chapter.step.ChapterValidateStep;
import com.novel.ai.agent.core.AuditErrorClassifier;
import com.novel.ai.agent.core.AuditPipeline;
import com.novel.ai.agent.core.AuditPipelineListener;
import com.novel.ai.agent.core.AuditStatusTags;
import com.novel.ai.agent.core.AuditStep;
import com.novel.ai.agent.core.CompositeAuditPipelineListener;
import com.novel.ai.agent.core.LoggingPipelineListener;
import com.novel.ai.agent.core.MicrometerPipelineListener;
import com.novel.ai.agent.core.SkywalkingPipelineListener;
import com.novel.book.dto.resp.ChapterAuditRespDto;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.function.Function;

/**
 * 装配章节审核流水线。顺序：参数校验 → 切分 → 逐段 RAG+LLM → 合并结果。
 */
@Configuration
public class ChapterAuditPipelineFactory {

    @Bean
    public AuditPipeline<ChapterAuditContext> chapterAuditPipeline(
            ChapterValidateStep validateStep,
            ChapterSensitiveWordFilterStep sensitiveWordFilterStep,
            ChapterSegmentStep segmentStep,
            ChapterSegmentAuditStep segmentAuditStep,
            ChapterResponseBuildStep responseStep,
            ChapterAuditExceptionMapper exceptionMapper,
            AuditErrorClassifier classifier,
            MeterRegistry meterRegistry) {
        List<AuditStep<ChapterAuditContext>> steps = List.of(
                validateStep, sensitiveWordFilterStep, segmentStep, segmentAuditStep, responseStep);
        Function<ChapterAuditContext, String> decisionExtractor = ctx -> {
            if (!ctx.hasResult()) {
                return "unknown";
            }
            ChapterAuditRespDto resp = ctx.getResult();
            return AuditStatusTags.of(resp.getAuditStatus());
        };
        Function<ChapterAuditContext, String> requestDescriptor = ctx -> {
            if (ctx.getRequest() == null) {
                return "";
            }
            return "bookId=" + ctx.getRequest().getBookId()
                    + " chapterNum=" + ctx.getRequest().getChapterNum();
        };
        AuditPipelineListener<ChapterAuditContext> listener = new CompositeAuditPipelineListener<>(
                new LoggingPipelineListener<>(requestDescriptor, decisionExtractor),
                new SkywalkingPipelineListener<>(classifier),
                new MicrometerPipelineListener<>(meterRegistry, classifier, decisionExtractor));
        return new AuditPipeline<>(steps, exceptionMapper, listener);
    }
}
