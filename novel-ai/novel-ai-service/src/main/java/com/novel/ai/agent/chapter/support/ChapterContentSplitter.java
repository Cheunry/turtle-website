package com.novel.ai.agent.chapter.support;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 将超长章节按"最大字符数 + 中文句末标点回退"切分。
 * 抽成独立组件后可被 Step 复用，也方便单测切分策略。
 */
@Component
public class ChapterContentSplitter {

    private static final int FALLBACK_WINDOW = 200;

    /**
     * 按 {@code maxLength} 切分；若某段末尾落在句子中间，尝试回退到最近的
     * 句号 / 问号 / 感叹号 / 换行符处断开，避免截断句子。
     */
    public List<String> split(String content, int maxLength) {
        List<String> segments = new ArrayList<>();
        if (content == null) {
            return segments;
        }
        if (content.length() <= maxLength) {
            segments.add(content);
            return segments;
        }

        int start = 0;
        while (start < content.length()) {
            int end = Math.min(start + maxLength, content.length());
            if (end < content.length()) {
                int breakPoint = findBreakPoint(content, start, end);
                if (breakPoint > start && breakPoint > end - FALLBACK_WINDOW) {
                    end = breakPoint + 1;
                }
            }
            segments.add(content.substring(start, end));
            start = end;
        }
        return segments;
    }

    private int findBreakPoint(String content, int start, int end) {
        int lastPeriod = content.lastIndexOf('。', end - 1);
        int lastQuestion = content.lastIndexOf('？', end - 1);
        int lastExclamation = content.lastIndexOf('！', end - 1);
        int lastNewline = content.lastIndexOf('\n', end - 1);
        return Math.max(Math.max(lastPeriod, lastQuestion),
                Math.max(lastExclamation, lastNewline));
    }
}
