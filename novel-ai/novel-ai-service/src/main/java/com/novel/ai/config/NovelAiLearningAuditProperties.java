package com.novel.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 学习资料类（默认 categoryId=8）专项审核：跳过本地敏感词与 RAG、独立 Prompt、可选作者绿色通道与分段策略。
 */
@Data
@ConfigurationProperties(prefix = "novel.ai.learning-audit")
public class NovelAiLearningAuditProperties {

    /**
     * 与 book_category / book_info.category_id 一致的学习资料类别 ID（主 ID，兼容原配置 {@code category-id}）。
     */
    private long categoryId = 8L;

    /**
     * 额外的学习资料类别 ID（多环境或库表多套 ID 时使用），与 {@link #categoryId} 合并判断。
     */
    private List<Long> extraCategoryIds = new ArrayList<>();

    /**
     * 类别名称完全等于其中任一项时，视为学习资料（兜底 MQ / 历史数据未带 categoryId 的情况）。
     */
    private List<String> categoryNameEqualsHints = new ArrayList<>(List.of("学习资料"));

    /**
     * 绿色通道作者 ID 白名单：命中时章节侧可用更大分段、可选正文截断上限以省 Token。
     * 不等于「免审」；免模型审核需同时打开 {@link #greenChannelSkipLlmForLearning}。
     */
    private Set<Long> greenChannelAuthorIds = new LinkedHashSet<>();

    /**
     * 为 true 时：作品属于学习资料类（见 {@link #matchesLearningCategory}）且作者在 {@link #greenChannelAuthorIds} 中，
     * 则书籍/章节审核跳过 LLM，直接返回通过（仍走参数校验；学习类仍跳过本地敏感词库）。
     */
    private boolean greenChannelSkipLlmForLearning = false;

    /**
     * 学习资料（非绿色通道）单段最大字符数，大于默认小说分段以减少 LLM 调用次数。
     */
    private int segmentChars = 12000;

    /**
     * 绿色通道单段最大字符数（通常更大 → 更少次调用）。
     */
    private int greenChannelSegmentChars = 32000;

    /**
     * 仅绿色通道：正文超过该长度时只送审前 N 个字符，0 表示不截断（全文送审）。
     */
    private int greenChannelMaxAuditChars = 0;

    public Set<Long> resolvedLearningCategoryIds() {
        Set<Long> s = new LinkedHashSet<>();
        s.add(categoryId);
        if (extraCategoryIds != null) {
            for (Long id : extraCategoryIds) {
                if (id != null) {
                    s.add(id);
                }
            }
        }
        return s;
    }

    public boolean isLearningCategory(Long categoryId) {
        return categoryId != null && resolvedLearningCategoryIds().contains(categoryId);
    }

    /**
     * ID 命中配置集合，或类别名称与 {@link #categoryNameEqualsHints} 完全一致时，走学习资料审核链路。
     */
    public boolean matchesLearningCategory(Long categoryId, String categoryName) {
        if (isLearningCategory(categoryId)) {
            return true;
        }
        if (categoryNameEqualsHints == null || categoryNameEqualsHints.isEmpty()) {
            return false;
        }
        String t = categoryName == null ? "" : categoryName.trim();
        if (t.isEmpty()) {
            return false;
        }
        for (String hint : categoryNameEqualsHints) {
            if (hint != null && t.equals(hint.trim())) {
                return true;
            }
        }
        return false;
    }

    public boolean isGreenChannel(Long authorId) {
        return authorId != null && greenChannelAuthorIds.contains(authorId);
    }

    /**
     * 学习资料 + 绿色通道作者 + 配置开启时，跳过模型调用。
     */
    public boolean shouldBypassLlmForLearning(Long categoryId, String categoryName, Long authorId) {
        if (!greenChannelSkipLlmForLearning) {
            return false;
        }
        return matchesLearningCategory(categoryId, categoryName) && isGreenChannel(authorId);
    }
}
