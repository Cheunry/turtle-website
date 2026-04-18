package com.novel.ai.agent.core;

import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.toolkit.trace.ActiveSpan;

/**
 * SkyWalking 链路追踪监听器：在流水线的关键节点打 Span Tag，便于在 APM
 * 平台上统一观察每一步的耗时、错误与状态，替代原来散落在业务代码里的
 * {@link ActiveSpan#tag(String, String)} 调用。
 *
 * <p>本监听器是无状态的，可作为单例复用。</p>
 *
 * @param <C> 上下文类型
 */
@Slf4j
public class SkywalkingPipelineListener<C extends AuditContext<?, ?>>
        implements AuditPipelineListener<C> {

    private static final int ERROR_MSG_MAX_LEN = 200;

    private final AuditErrorClassifier classifier;

    public SkywalkingPipelineListener(AuditErrorClassifier classifier) {
        this.classifier = classifier;
    }

    @Override
    public void onStart(C context) {
        ActiveSpan.tag("ai.operation", context.getOperationName());
    }

    @Override
    public void onStepStart(C context, AuditStep<C> step) {
        ActiveSpan.tag("ai.step.current", step.name());
    }

    @Override
    public void onStepEnd(C context, AuditStep<C> step, StepResult result) {
        if (result == StepResult.SHORT_CIRCUIT) {
            ActiveSpan.tag("ai.step.short_circuit", step.name());
        }
    }

    @Override
    public void onStepError(C context, AuditStep<C> step, Exception e) {
        ActiveSpan.tag("ai.status", "error");
        ActiveSpan.tag("ai.error.step", step.name());
        ActiveSpan.tag("ai.error.type", e.getClass().getSimpleName());

        AuditErrorCategory category = classifier.classify(e);
        ActiveSpan.tag("ai.error.category", category.name().toLowerCase());

        String msg = e.getMessage();
        if (msg != null && msg.length() > ERROR_MSG_MAX_LEN) {
            msg = msg.substring(0, ERROR_MSG_MAX_LEN);
        }
        ActiveSpan.tag("ai.error.message", msg != null ? msg : "unknown");
        ActiveSpan.error(e);
    }

    @Override
    public void onEnd(C context) {
        long duration = System.currentTimeMillis() - context.getStartTimeMs();
        ActiveSpan.tag("ai.duration.ms", String.valueOf(duration));
        if (context.getError() == null) {
            // 成功或短路都视为业务成功；真正异常在 onStepError 里已打过 error tag
            ActiveSpan.tag("ai.status", "success");
        }
    }
}
