package com.novel.ai.agent.core;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 把多个 {@link AuditPipelineListener} 组合成一个，用于同时挂载 SkyWalking、Micrometer
 * 等横切观测组件。任一 listener 抛出异常都不会影响后续 listener 的执行。
 *
 * @param <C> 上下文类型
 */
@Slf4j
public class CompositeAuditPipelineListener<C extends AuditContext<?, ?>> implements AuditPipelineListener<C> {

    private final List<AuditPipelineListener<C>> delegates;

    public CompositeAuditPipelineListener(List<AuditPipelineListener<C>> delegates) {
        this.delegates = new ArrayList<>(delegates == null ? List.of() : delegates);
    }

    @SafeVarargs
    public CompositeAuditPipelineListener(AuditPipelineListener<C>... delegates) {
        this(delegates == null ? List.of() : Arrays.asList(delegates));
    }

    @Override
    public void onStart(C context) {
        for (AuditPipelineListener<C> listener : delegates) {
            safeRun(listener, "onStart", l -> l.onStart(context));
        }
    }

    @Override
    public void onStepStart(C context, AuditStep<C> step) {
        for (AuditPipelineListener<C> listener : delegates) {
            safeRun(listener, "onStepStart", l -> l.onStepStart(context, step));
        }
    }

    @Override
    public void onStepEnd(C context, AuditStep<C> step, StepResult result) {
        for (AuditPipelineListener<C> listener : delegates) {
            safeRun(listener, "onStepEnd", l -> l.onStepEnd(context, step, result));
        }
    }

    @Override
    public void onStepError(C context, AuditStep<C> step, Exception e) {
        for (AuditPipelineListener<C> listener : delegates) {
            safeRun(listener, "onStepError", l -> l.onStepError(context, step, e));
        }
    }

    @Override
    public void onEnd(C context) {
        for (AuditPipelineListener<C> listener : delegates) {
            safeRun(listener, "onEnd", l -> l.onEnd(context));
        }
    }

    private void safeRun(AuditPipelineListener<C> listener, String hook, ListenerAction<C> action) {
        try {
            action.accept(listener);
        } catch (Exception ex) {
            log.warn("[CompositeAuditPipelineListener] listener {} 在 {} 钩子抛异常，已吞掉不影响主流程",
                    listener.getClass().getSimpleName(), hook, ex);
        }
    }

    @FunctionalInterface
    private interface ListenerAction<C extends AuditContext<?, ?>> {
        void accept(AuditPipelineListener<C> listener) throws Exception;
    }
}
