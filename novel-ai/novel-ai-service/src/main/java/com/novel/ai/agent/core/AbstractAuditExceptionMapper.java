package com.novel.ai.agent.core;

import lombok.RequiredArgsConstructor;

/**
 * 通用异常映射模板：根据 {@link AuditErrorClassifier} 的分类结果，调用
 * 子类提供的两个钩子生成具体业务响应。
 *
 * @param <C> 上下文类型
 */
@RequiredArgsConstructor
public abstract class AbstractAuditExceptionMapper<C extends AuditContext<?, ?>>
        implements AuditExceptionMapper<C> {

    protected final AuditErrorClassifier classifier;

    @Override
    public void mapToResult(C context, Exception error) {
        AuditErrorCategory category = classifier.classify(error);
        if (category == AuditErrorCategory.CONTENT_INSPECTION_FAILED) {
            context.setResult(castResult(buildInspectionRejectedResult(context)));
        } else {
            context.setResult(castResult(buildFallbackResult(context, error)));
        }
    }

    /**
     * 内容安全拦截时的响应（通常：auditStatus=2，置信度=1.0，理由=固定文案）。
     */
    protected abstract Object buildInspectionRejectedResult(C context);

    /**
     * 其他异常的降级响应（通常：auditStatus=0，置信度=0.0，理由="已进入人工审核"）。
     */
    protected abstract Object buildFallbackResult(C context, Exception error);

    /**
     * 由子类保证返回类型与上下文 RESP 一致；这里用受检转换统一收口。
     */
    @SuppressWarnings("unchecked")
    private <R> R castResult(Object obj) {
        return (R) obj;
    }
}
