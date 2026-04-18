package com.novel.ai.model;

/**
 * AI 文本润色的中间结构化输出。
 *
 * @param polishedText 润色后的文本
 * @param explanation  润色说明（修改了哪里、为什么）
 */
public record TextPolishAiOutput(
        String polishedText,
        String explanation
) {
}
