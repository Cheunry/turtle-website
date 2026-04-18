package com.novel.ai.agent.support;

/**
 * Prompt 模板常用工具。
 */
public final class PromptVars {

    private PromptVars() {}

    /** 把 null 归一成空字符串，避免 PromptTemplate 占位符解析失败。 */
    public static String safe(String value) {
        return value == null ? "" : value;
    }
}
