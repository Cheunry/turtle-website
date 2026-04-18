package com.novel.ai.agent.core;

import lombok.extern.slf4j.Slf4j;

import java.util.function.Function;

/**
 * 流水线日志监听器：用统一前缀 {@code [AI-Audit]} 打印流水线节拍，确保"肉眼可见"。
 *
 * <p>日志级别策略：</p>
 * <ul>
 *     <li>INFO：流水线开始 / 结束 / 某步短路。{@code grep "AI-Audit"} 即可看到整体链路。</li>
 *     <li>DEBUG：每个步骤的开始 / 正常完成，生产可关，调优时临时打开。</li>
 *     <li>WARN：某步抛异常（具体分类由其它 listener 兜底）。</li>
 * </ul>
 *
 * <p>使用方式：在 Factory 里作为 Composite 的第一个 listener 注入，顺序上它需要先于
 * Micrometer 读取 {@link AuditContext#getStepStartNanos()}，保证耗时可读。</p>
 *
 * @param <C> 上下文类型
 */
@Slf4j
public class LoggingPipelineListener<C extends AuditContext<?, ?>> implements AuditPipelineListener<C> {

    private final Function<C, String> requestDescriptor;
    private final Function<C, String> decisionStatusExtractor;

    public LoggingPipelineListener(Function<C, String> requestDescriptor,
                                   Function<C, String> decisionStatusExtractor) {
        this.requestDescriptor = requestDescriptor != null ? requestDescriptor : c -> "";
        this.decisionStatusExtractor = decisionStatusExtractor != null ? decisionStatusExtractor : c -> "unknown";
    }

    @Override
    public void onStart(C context) {
        log.info("[AI-Audit] ▶ {} 开始 {}",
                context.getOperationName(), requestDescriptor.apply(context));
    }

    @Override
    public void onStepStart(C context, AuditStep<C> step) {
        log.debug("[AI-Audit]   ↳ {} step={} 开始",
                context.getOperationName(), step.name());
    }

    @Override
    public void onStepEnd(C context, AuditStep<C> step, StepResult result) {
        long costMs = elapsedMs(context, step);
        if (result == StepResult.SHORT_CIRCUIT) {
            log.info("[AI-Audit]   ⚡ {} step={} 短路 cost={}ms",
                    context.getOperationName(), step.name(), costMs);
        } else {
            log.debug("[AI-Audit]   ✓ {} step={} 完成 cost={}ms",
                    context.getOperationName(), step.name(), costMs);
        }
    }

    @Override
    public void onStepError(C context, AuditStep<C> step, Exception e) {
        long costMs = elapsedMs(context, step);
        log.warn("[AI-Audit]   ✗ {} step={} 失败 cost={}ms error={}: {}",
                context.getOperationName(), step.name(), costMs,
                e.getClass().getSimpleName(), e.getMessage());
    }

    @Override
    public void onEnd(C context) {
        long durationMs = System.currentTimeMillis() - context.getStartTimeMs();
        String outcome;
        if (context.getError() != null) {
            outcome = "ERROR";
        } else if (context.hasResult()) {
            outcome = "OK";
        } else {
            outcome = "NO_RESULT";
        }
        log.info("[AI-Audit] ■ {} 结束 {} outcome={} decision={} duration={}ms",
                context.getOperationName(),
                requestDescriptor.apply(context),
                outcome,
                decisionStatusExtractor.apply(context),
                durationMs);
    }

    private long elapsedMs(C context, AuditStep<C> step) {
        Long startNs = context.getStepStartNanos().get(step.name());
        return startNs == null ? 0L : (System.nanoTime() - startNs) / 1_000_000L;
    }
}
