package com.novel.ai.agent.support;

import com.novel.ai.config.NovelAiAuditCategoryProperties;
import com.novel.book.dto.req.BookAuditReqDto;
import com.novel.book.dto.req.ChapterAuditReqDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 根据请求中的书籍类别（book_info.category_id），从配置中解析「类别附加审核说明」，拼入 system prompt。
 */
@Component
@RequiredArgsConstructor
public class AuditCategoryPromptResolver {

    private final NovelAiAuditCategoryProperties properties;

    public String resolveSystemExtra(BookAuditReqDto req) {
        if (req == null) {
            return "";
        }
        return resolve(req.getCategoryId());
    }

    public String resolveSystemExtra(ChapterAuditReqDto req) {
        if (req == null) {
            return "";
        }
        return resolve(req.getCategoryId());
    }

    /**
     * 命中 {@code category-guidelines[categoryId]} 则返回；否则返回 {@code default-guidelines}。
     */
    public String resolve(Long categoryId) {
        String fromCategory = lookupTrimmed(properties.getCategoryGuidelines(), key(categoryId));
        if (!fromCategory.isEmpty()) {
            return fromCategory;
        }
        String def = properties.getDefaultGuidelines();
        return def == null ? "" : def.trim();
    }

    private static String key(Long id) {
        return id == null ? null : String.valueOf(id);
    }

    private static String lookupTrimmed(Map<String, String> map, String key) {
        if (key == null || map == null || map.isEmpty()) {
            return "";
        }
        String v = map.get(key);
        return v == null ? "" : v.trim();
    }
}
