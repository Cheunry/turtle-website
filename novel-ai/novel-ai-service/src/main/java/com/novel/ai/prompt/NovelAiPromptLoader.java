package com.novel.ai.prompt;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;

/**
 * 小说 AI 模块 Prompt 模板加载器。
 * <p>
 * - 启动阶段一次性读取 {@code resources/prompts/*.st}，在内存中构造 {@link PromptTemplate} 并缓存。
 * - 对外只暴露 system / user 两类模板的获取方法，业务层通过 {@link NovelAiPromptKey} 选择场景。
 * - 后续若需要支持动态热更新（例如从 Nacos 拉取 prompt），只需改造本类的加载入口即可，业务层不感知。
 */
@Slf4j
@Component
public class NovelAiPromptLoader {

    private final ResourceLoader resourceLoader;

    private final Map<NovelAiPromptKey, PromptTemplate> systemTemplates = new EnumMap<>(NovelAiPromptKey.class);
    private final Map<NovelAiPromptKey, PromptTemplate> userTemplates = new EnumMap<>(NovelAiPromptKey.class);

    public NovelAiPromptLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    public void init() {
        for (NovelAiPromptKey key : NovelAiPromptKey.values()) {
            PromptTemplate system = loadTemplate(key.systemPath());
            systemTemplates.put(key, system);
            userTemplates.put(key, loadTemplate(key.userPath()));
            // 启动自检：system 模板都是无变量的静态 prompt，立即 render 一次，
            // 任何 `{...}` 被 ST 误当表达式解析的情况都会在启动阶段暴露，而不是等到线上请求。
            try {
                system.render();
            } catch (RuntimeException e) {
                throw new IllegalStateException(
                        "system prompt 模板语法错误，请检查 " + key.systemPath()
                                + "（常见原因：模板中直接写了示例 JSON 的 {...}，会被 StringTemplate 当成表达式解析）", e);
            }
        }
        log.info("[NovelAiPromptLoader] 已加载 {} 组 Prompt 模板 (system + user)", NovelAiPromptKey.values().length);
    }

    /**
     * 获取 system 提示词模板。
     */
    public PromptTemplate system(NovelAiPromptKey key) {
        PromptTemplate template = systemTemplates.get(key);
        if (template == null) {
            throw new IllegalStateException("system prompt not initialized for key: " + key);
        }
        return template;
    }

    /**
     * 获取 user 提示词模板。
     */
    public PromptTemplate user(NovelAiPromptKey key) {
        PromptTemplate template = userTemplates.get(key);
        if (template == null) {
            throw new IllegalStateException("user prompt not initialized for key: " + key);
        }
        return template;
    }

    /**
     * 渲染 system 提示词（无变量场景）。
     */
    public String renderSystem(NovelAiPromptKey key) {
        return system(key).render();
    }

    /**
     * 使用变量渲染 system 提示词。
     */
    public String renderSystem(NovelAiPromptKey key, Map<String, Object> variables) {
        return system(key).render(variables);
    }

    /**
     * 使用变量渲染 user 提示词。
     */
    public String renderUser(NovelAiPromptKey key, Map<String, Object> variables) {
        return user(key).render(variables);
    }

    private PromptTemplate loadTemplate(String location) {
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            throw new IllegalStateException("prompt resource not found: " + location);
        }
        try {
            String content = resource.getContentAsString(StandardCharsets.UTF_8);
            return new PromptTemplate(content);
        } catch (IOException e) {
            throw new IllegalStateException("failed to load prompt resource: " + location, e);
        }
    }
}
