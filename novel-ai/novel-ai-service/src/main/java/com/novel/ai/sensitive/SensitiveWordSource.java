package com.novel.ai.sensitive;

import java.util.Collection;

/**
 * 敏感词字典数据源抽象。当前实现为本地文件，后续可新增 Nacos 实现
 * 以支持热更新，{@link SensitiveWordMatcher} 透明切换。
 */
public interface SensitiveWordSource {

    /**
     * 加载字典。返回值内部可重复、可包含空白；{@link SensitiveWordMatcher}
     * 会统一去重与裁剪空白。实现方只需保证"能拿到当前应生效的词表"。
     */
    Collection<String> loadWords();
}
