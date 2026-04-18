package com.novel.ai.agent.core;

/**
 * 流水线执行钩子，用于解耦业务与横切关注点（观测、日志、指标等）。
 * 默认实现为 {@link #noop()}，不做任何事。
 *
 * @param <C> 上下文类型
 */
public interface AuditPipelineListener<C extends AuditContext<?, ?>> {

    default void onStart(C context) {}

    default void onStepStart(C context, AuditStep<C> step) {}

    default void onStepEnd(C context, AuditStep<C> step, StepResult result) {}

    default void onStepError(C context, AuditStep<C> step, Exception e) {}

    default void onEnd(C context) {}

    @SuppressWarnings("unchecked")
    static <C extends AuditContext<?, ?>> AuditPipelineListener<C> noop() {
        return (AuditPipelineListener<C>) NoopHolder.INSTANCE;
    }

    final class NoopHolder {
        private static final AuditPipelineListener<?> INSTANCE = new AuditPipelineListener<>() {};
        private NoopHolder() {}
    }
}
