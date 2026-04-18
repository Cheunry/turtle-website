package com.novel.ai.prompt;

/**
 * 小说 AI 模块的 Prompt 场景枚举，每个场景对应一组 system + user 模板文件。
 * 模板文件位于 classpath:prompts/ 目录下，文件名规则：
 *   - system: {fileBase}-system.st
 *   - user:   {fileBase}-user.st
 */
public enum NovelAiPromptKey {

    /** 书籍审核（小说名 + 简介） */
    BOOK_AUDIT("book-audit"),

    /** 章节审核（章节名 + 正文，支持分段） */
    CHAPTER_AUDIT("chapter-audit"),

    /** 文本润色 */
    TEXT_POLISH("text-polish"),

    /** 封面提示词生成 */
    COVER_PROMPT("cover-prompt"),

    /** 审核判例规则抽取（RAG 沉淀） */
    AUDIT_RULE_EXTRACT("audit-rule-extract");

    private final String fileBase;

    NovelAiPromptKey(String fileBase) {
        this.fileBase = fileBase;
    }

    public String systemPath() {
        return "classpath:prompts/" + fileBase + "-system.st";
    }

    public String userPath() {
        return "classpath:prompts/" + fileBase + "-user.st";
    }
}
