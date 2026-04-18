package com.novel.ai.agent.core;

/**
 * 把流水线执行中抛出的异常翻译为具体业务响应，屏蔽原来散落在各处的
 * try-catch + isContentInspectionFailed 判断。
 *
 * <p>典型实现按两类分派：</p>
 * <ol>
 *     <li>AI 模型内容安全拦截（DataInspectionFailed 等）→ 直接返回"审核不通过"；</li>
 *     <li>其余异常 → 返回"待人工审核"。</li>
 * </ol>
 *
 * @param <C> 上下文类型
 */
@FunctionalInterface
public interface AuditExceptionMapper<C extends AuditContext<?, ?>> {

    /**
     * 根据异常给 context 填充最终结果。实现方应保证方法返回后
     * {@link AuditContext#getResult()} 非空。
     */
    void mapToResult(C context, Exception error);
}
