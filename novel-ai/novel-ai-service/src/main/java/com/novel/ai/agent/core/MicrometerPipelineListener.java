package com.novel.ai.agent.core;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * 基于 Micrometer 的流水线观测监听器，统一输出以下指标（均带 {@code operation} 标签）：
 * <ul>
 *     <li>{@code novel.ai.audit.duration} —— 流水线总耗时 Timer，区分
 *         {@code outcome=success|short_circuit|error} 和 {@code error_category}。</li>
 *     <li>{@code novel.ai.audit.step.duration} —— 单步耗时 Timer，区分
 *         {@code step} 和 {@code outcome}（continue / short_circuit / error）。</li>
 *     <li>{@code novel.ai.audit.decision} —— 最终决策 Counter，{@code status}
 *         维度用于观察通过率（passed / rejected / pending / unknown）。</li>
 * </ul>
 *
 * <p>Listener 本身无状态，实例可被多个 Pipeline 并发共享。</p>
 *
 * @param <C> 上下文类型
 */
@Slf4j
public class MicrometerPipelineListener<C extends AuditContext<?, ?>> implements AuditPipelineListener<C> {

    public static final String METRIC_DURATION = "novel.ai.audit.duration";
    public static final String METRIC_STEP_DURATION = "novel.ai.audit.step.duration";
    public static final String METRIC_DECISION = "novel.ai.audit.decision";

    private final MeterRegistry registry;
    private final AuditErrorClassifier classifier;
    /** 从上下文解析业务决策状态（passed/rejected/pending/unknown）的函数。 */
    private final Function<C, String> decisionStatusExtractor;

    public MicrometerPipelineListener(MeterRegistry registry,
                                      AuditErrorClassifier classifier,
                                      Function<C, String> decisionStatusExtractor) {
        this.registry = registry;
        this.classifier = classifier;
        this.decisionStatusExtractor = decisionStatusExtractor != null
                ? decisionStatusExtractor
                : ctx -> "unknown";
    }

    public MicrometerPipelineListener(MeterRegistry registry, AuditErrorClassifier classifier) {
        this(registry, classifier, ctx -> "unknown");
    }

    @Override
    public void onStepStart(C context, AuditStep<C> step) {
        context.getStepStartNanos().put(step.name(), System.nanoTime());
    }

    @Override
    public void onStepEnd(C context, AuditStep<C> step, StepResult result) {
        recordStep(context, step, outcomeOf(result), null);
    }

    @Override
    public void onStepError(C context, AuditStep<C> step, Exception e) {
        recordStep(context, step, "error", classifier.classify(e));
    }

    @Override
    public void onEnd(C context) {
        long durationMs = System.currentTimeMillis() - context.getStartTimeMs();
        String outcome;
        String errorCategory;
        if (context.getError() != null) {
            outcome = "error";
            errorCategory = classifier.classify(context.getError()).name().toLowerCase();
        } else {
            outcome = context.hasResult() ? "success" : "no_result";
            errorCategory = "none";
        }

        Timer.builder(METRIC_DURATION)
                .tag("operation", safeTag(context.getOperationName()))
                .tag("outcome", outcome)
                .tag("error_category", errorCategory)
                .register(registry)
                .record(durationMs, TimeUnit.MILLISECONDS);

        String status = safeTag(decisionStatusExtractor.apply(context));
        registry.counter(METRIC_DECISION,
                        Tags.of("operation", safeTag(context.getOperationName()),
                                "status", status))
                .increment();
    }

    private void recordStep(C context, AuditStep<C> step, String outcome, AuditErrorCategory errorCategory) {
        Long startNs = context.getStepStartNanos().get(step.name());
        long elapsed = startNs == null ? 0L : System.nanoTime() - startNs;
        Tags tags = Tags.of(
                "operation", safeTag(context.getOperationName()),
                "step", safeTag(step.name()),
                "outcome", outcome,
                "error_category", errorCategory == null ? "none" : errorCategory.name().toLowerCase()
        );
        Timer.builder(METRIC_STEP_DURATION)
                .tags(tags)
                .register(registry)
                .record(elapsed, TimeUnit.NANOSECONDS);
    }

    private static String outcomeOf(StepResult result) {
        if (result == null) {
            return "unknown";
        }
        return result == StepResult.SHORT_CIRCUIT ? "short_circuit" : "continue";
    }

    private static String safeTag(String value) {
        return value == null || value.isEmpty() ? "unknown" : value;
    }
}
