package com.novel.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 按书籍类别附加的审核说明，由 Nacos 或本地配置维护。
 * <p>
 * 解析优先级：{@code category-guidelines[categoryId]} &gt; {@code default-guidelines}。
 */
@Data
@ConfigurationProperties(prefix = "novel.ai.audit-category")
public class NovelAiAuditCategoryProperties {

    /**
     * key：类别 ID 字符串（与 book_info.category_id 一致），例如 "12"。
     */
    private Map<String, String> categoryGuidelines = new LinkedHashMap<>();

    /**
     * 未命中具体类别时的兜底说明；可为空。
     */
    private String defaultGuidelines = "";
}
