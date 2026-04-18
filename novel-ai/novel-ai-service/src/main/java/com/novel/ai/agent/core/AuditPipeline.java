package com.novel.ai.agent.core;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Objects;

/**
 * 责任链式的审核流水线。按声明顺序依次执行 {@link AuditStep}，并统一处理：
 * <ul>
 *     <li>短路：某步返回 {@link StepResult#SHORT_CIRCUIT} 则立即结束；</li>
 *     <li>异常：任一步抛出异常时捕获并交给 {@link AuditExceptionMapper} 翻译成业务响应；</li>
 *     <li>观测：每步执行前后、异常时回调 {@link AuditPipelineListener}。</li>
 * </ul>
 *
 * <p>本类是无状态的，一个 Pipeline 实例可被多线程共享使用。</p>
 *
 * @param <C> 上下文类型
 */
@Slf4j
public class AuditPipeline<C extends AuditContext<?, ?>> {

    private final List<AuditStep<C>> steps;
    private final AuditExceptionMapper<C> exceptionMapper;
    private final AuditPipelineListener<C> listener;

    public AuditPipeline(List<AuditStep<C>> steps,
                         AuditExceptionMapper<C> exceptionMapper,
                         AuditPipelineListener<C> listener) {
        this.steps = Objects.requireNonNull(steps, "steps");
        this.exceptionMapper = Objects.requireNonNull(exceptionMapper, "exceptionMapper");
        this.listener = listener != null ? listener : AuditPipelineListener.noop();
    }

    /**
     * 执行整条流水线。正常返回后，调用方应从 {@link AuditContext#getResult()} 取最终响应。
     */
    public void execute(C context) {
        listener.onStart(context);
        try {
            for (AuditStep<C> step : steps) {
                listener.onStepStart(context, step);
                StepResult result;
                try {
                    result = step.execute(context);
                } catch (Exception e) {
                    listener.onStepError(context, step, e);
                    context.setError(e);
                    exceptionMapper.mapToResult(context, e);
                    return;
                }
                listener.onStepEnd(context, step, result);
                if (result == StepResult.SHORT_CIRCUIT) {
                    log.debug("[AuditPipeline] 在步骤 {} 短路，operation={}", step.name(), context.getOperationName());
                    return;
                }
            }
        } finally {
            listener.onEnd(context);
        }
    }
}
