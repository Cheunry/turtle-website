package com.novel.ai.agent.support;

import com.novel.ai.config.NovelAiAuditCategoryProperties;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuditCategoryPromptResolverTest {

    @Test
    void uses_category_guidelines_when_present() {
        NovelAiAuditCategoryProperties p = new NovelAiAuditCategoryProperties();
        p.setCategoryGuidelines(Map.of("5", "  玄幻专用  "));
        p.setDefaultGuidelines("默认");
        AuditCategoryPromptResolver r = new AuditCategoryPromptResolver(p);

        assertThat(r.resolve(5L)).isEqualTo("玄幻专用");
    }

    @Test
    void falls_back_to_default_when_category_unknown() {
        NovelAiAuditCategoryProperties p = new NovelAiAuditCategoryProperties();
        p.setCategoryGuidelines(Map.of());
        p.setDefaultGuidelines("默认");
        AuditCategoryPromptResolver r = new AuditCategoryPromptResolver(p);

        assertThat(r.resolve(999L)).isEqualTo("默认");
    }
}
