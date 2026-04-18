package com.novel.ai.agent.core;

import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.stereotype.Component;

/**
 * 对流水线抛出的异常进行分类：是否属于"AI 内容安全拦截"。
 *
 * <p>原 {@code TextServiceImpl.isContentInspectionFailed} 的判定逻辑搬到这里，
 * 以便所有业务 Mapper / 监听器复用。</p>
 */
@Component
public class AuditErrorClassifier {

    /**
     * 判断异常是否为 AI 模型内容安全拦截。典型特征：
     * <ul>
     *     <li>Spring AI 抛出的 {@link NonTransientAiException}；</li>
     *     <li>或异常消息包含 {@code DataInspectionFailed}、{@code inappropriate content}、
     *         "不当内容"、"内容安全检查" 等关键词。</li>
     * </ul>
     */
    public boolean isContentInspectionFailed(Throwable e) {
        if (e == null) {
            return false;
        }
        if (e instanceof NonTransientAiException && containsInspectionKeyword(e.getMessage(), true)) {
            return true;
        }
        return containsInspectionKeyword(e.getMessage(), false);
    }

    public AuditErrorCategory classify(Throwable e) {
        return isContentInspectionFailed(e)
                ? AuditErrorCategory.CONTENT_INSPECTION_FAILED
                : AuditErrorCategory.OTHER;
    }

    /**
     * @param onlyEnglishKeywords 为 true 时仅匹配英文关键字，避免把"内容安全检查"这种
     *                            通用业务文案误判为 AI 模型拦截。
     */
    private boolean containsInspectionKeyword(String message, boolean onlyEnglishKeywords) {
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase();
        if (lower.contains("datainspectionfailed") || lower.contains("inappropriate content")) {
            return true;
        }
        if (onlyEnglishKeywords) {
            return false;
        }
        return lower.contains("不当内容") || lower.contains("内容安全检查");
    }
}
