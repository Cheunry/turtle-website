package com.novel.ai.prompt;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 启动期 Prompt 自检回归测试。
 *
 * <p>目的：防止以后有人在 {@code *-system.st} 里直接写裸露的 JSON 示例 {@code { ... }}，
 * 触发 StringTemplate4 "mismatched input ..." 类语法错误——这种错误只会在运行时调用
 * AI 时暴露，日志里才出现 "invalid character" / "mismatched input"，非常难排查。</p>
 *
 * <p>只要这个测试挂了，就说明有 system 模板语法错，立刻会被 CI 拦住。</p>
 */
class NovelAiPromptLoaderSmokeTest {

    @Test
    void allSystemPromptsShouldRenderWithoutSyntaxError() {
        NovelAiPromptLoader loader = new NovelAiPromptLoader(new DefaultResourceLoader());
        loader.init();
        for (NovelAiPromptKey key : NovelAiPromptKey.values()) {
            String rendered = loader.renderSystem(key);
            assertThat(rendered).as("system prompt for %s", key).isNotBlank();
        }
    }
}
