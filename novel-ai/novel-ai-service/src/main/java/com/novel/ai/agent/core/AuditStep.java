package com.novel.ai.agent.core;

/**
 * 审核流水线中的一个原子步骤。实现类应保持职责单一、可单测，
 * 所有协作数据读写 {@link AuditContext}。
 *
 * @param <C> 上下文类型
 */
public interface AuditStep<C extends AuditContext<?, ?>> {

    /**
     * 执行本步骤。
     *
     * @return {@link StepResult#CONTINUE} 继续下一步；{@link StepResult#SHORT_CIRCUIT}
     *         结束整个流水线（此时 {@link AuditContext#getResult()} 必须已被填充）。
     */
    StepResult execute(C context);

    /**
     * 步骤名，用于日志、链路追踪 tag 与异常排查。默认取类名便于快速上手，
     * 实现类可覆写为更语义化的名字。
     */
    default String name() {
        return getClass().getSimpleName();
    }
}
