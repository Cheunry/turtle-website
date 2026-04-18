package com.novel.ai.sensitive;

import com.hankcs.algorithm.AhoCorasickDoubleArrayTrie;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 基于 Aho-Corasick 双数组 Trie 的敏感词匹配器。启动时从
 * {@link SensitiveWordSource} 加载词典、构建自动机；运行时对外提供
 * 一次 O(n) 的多模式串扫描。
 *
 * <p>特性：
 * <ul>
 *     <li>词典为空 / 过滤器关闭 ⇒ 匹配器处于 disabled 状态，{@link #findAll(String)} 始终返回空；</li>
 *     <li>{@link SensitiveWordProperties#isIgnoreCase()} 控制大小写折叠；</li>
 *     <li>相同命中词会去重，保留首次出现顺序。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SensitiveWordMatcher {

    private final SensitiveWordSource source;
    private final SensitiveWordProperties properties;

    private volatile AhoCorasickDoubleArrayTrie<String> trie;
    private volatile boolean enabled;
    private volatile boolean ignoreCase;

    @PostConstruct
    public void init() {
        refresh();
    }

    /**
     * 重新构建自动机。预留给未来 Nacos 热更新调用。
     */
    public synchronized void refresh() {
        this.ignoreCase = properties.isIgnoreCase();
        if (!properties.isEnabled()) {
            this.trie = null;
            this.enabled = false;
            log.info("[SensitiveWord] 过滤器已关闭（enabled=false），跳过字典加载");
            return;
        }

        Collection<String> raw = source.loadWords();
        Set<String> dedup = new LinkedHashSet<>();
        for (String w : raw) {
            if (w == null) {
                continue;
            }
            String trimmed = w.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            dedup.add(ignoreCase ? trimmed.toLowerCase() : trimmed);
        }
        if (dedup.isEmpty()) {
            this.trie = null;
            this.enabled = false;
            log.warn("[SensitiveWord] 字典为空，敏感词过滤将不生效");
            return;
        }

        Map<String, String> map = new LinkedHashMap<>();
        for (String word : dedup) {
            map.put(word, word);
        }
        AhoCorasickDoubleArrayTrie<String> newTrie = new AhoCorasickDoubleArrayTrie<>();
        newTrie.build(map);
        this.trie = newTrie;
        this.enabled = true;
        log.info("[SensitiveWord] 敏感词字典加载完成，词条数={}，ignoreCase={}", dedup.size(), ignoreCase);
    }

    /** 匹配器是否处于可用状态。 */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 扫描文本，返回所有命中的敏感词（去重、保持首次出现顺序）。
     * 文本为 null / 空、或匹配器被禁用时返回空列表。
     */
    public List<String> findAll(String text) {
        if (text == null || text.isEmpty() || !enabled) {
            return List.of();
        }
        String target = ignoreCase ? text.toLowerCase() : text;
        AhoCorasickDoubleArrayTrie<String> current = this.trie;
        if (current == null) {
            return List.of();
        }
        Set<String> hits = new LinkedHashSet<>();
        AhoCorasickDoubleArrayTrie.IHit<String> collector = (begin, end, value) -> hits.add(value);
        current.parseText(target, collector);
        return new ArrayList<>(hits);
    }

    /** 文本中是否命中任一敏感词。基于 IHitCancellable 真正提前终止扫描。 */
    public boolean hasAny(String text) {
        if (text == null || text.isEmpty() || !enabled) {
            return false;
        }
        String target = ignoreCase ? text.toLowerCase() : text;
        AhoCorasickDoubleArrayTrie<String> current = this.trie;
        if (current == null) {
            return false;
        }
        boolean[] hit = new boolean[1];
        AhoCorasickDoubleArrayTrie.IHitCancellable<String> cancellable = (begin, end, value) -> {
            hit[0] = true;
            return false; // 返回 false 中止遍历
        };
        current.parseText(target, cancellable);
        return hit[0];
    }
}
