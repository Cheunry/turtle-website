package com.novel.ai.agent.book;

import com.novel.ai.agent.book.step.BookLlmInvokeStep;
import com.novel.ai.agent.book.step.BookPromptAssembleStep;
import com.novel.ai.agent.book.step.BookRagRetrieveStep;
import com.novel.ai.agent.book.step.BookResponseBuildStep;
import com.novel.ai.agent.book.step.BookSensitiveWordFilterStep;
import com.novel.ai.agent.book.step.BookValidateStep;
import com.novel.ai.agent.core.AuditErrorClassifier;
import com.novel.ai.agent.core.AuditPipeline;
import com.novel.ai.agent.core.AuditPipelineListener;
import com.novel.ai.agent.core.AuditStatusTags;
import com.novel.ai.agent.core.AuditStep;
import com.novel.ai.agent.core.CompositeAuditPipelineListener;
import com.novel.ai.agent.core.LoggingPipelineListener;
import com.novel.ai.agent.core.MicrometerPipelineListener;
import com.novel.ai.agent.core.SkywalkingPipelineListener;
import com.novel.book.dto.resp.BookAuditRespDto;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.function.Function;

/**
 * 装配书籍审核流水线。只在这里决定 Step 顺序，业务方（{@code TextServiceImpl}）
 * 只需拿 Bean 调用 {@link AuditPipeline#execute(com.novel.ai.agent.core.AuditContext)}。
 */
@Configuration
@RequiredArgsConstructor
public class BookAuditPipelineFactory {

    @Bean
    public AuditPipeline<BookAuditContext> bookAuditPipeline(
            BookValidateStep validateStep,
            BookSensitiveWordFilterStep sensitiveWordFilterStep,
            BookRagRetrieveStep ragStep,
            BookPromptAssembleStep promptStep,
            BookLlmInvokeStep llmStep,
            BookResponseBuildStep responseStep,
            BookAuditExceptionMapper exceptionMapper,
            AuditErrorClassifier classifier,
            MeterRegistry meterRegistry) {
        List<AuditStep<BookAuditContext>> steps = List.of(
                validateStep, sensitiveWordFilterStep, ragStep, promptStep, llmStep, responseStep);
        Function<BookAuditContext, String> decisionExtractor = ctx -> {
            if (!ctx.hasResult()) {
                return "unknown";
            }
            BookAuditRespDto resp = ctx.getResult();
            return AuditStatusTags.of(resp.getAuditStatus());
        };
        Function<BookAuditContext, String> requestDescriptor = ctx -> {
            Object id = ctx.getRequest() == null ? null : ctx.getRequest().getId();
            return "bookId=" + id;
        };
        AuditPipelineListener<BookAuditContext> listener = new CompositeAuditPipelineListener<>(
                new LoggingPipelineListener<>(requestDescriptor, decisionExtractor),
                new SkywalkingPipelineListener<>(classifier),
                new MicrometerPipelineListener<>(meterRegistry, classifier, decisionExtractor));
        return new AuditPipeline<>(steps, exceptionMapper, listener);
    }
}
