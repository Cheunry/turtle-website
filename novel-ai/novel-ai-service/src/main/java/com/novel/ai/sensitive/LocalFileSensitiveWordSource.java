package com.novel.ai.sensitive;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 从本地资源文件加载敏感词。约定：
 * <ul>
 *     <li>一行一个词；</li>
 *     <li>以 {@code #} 开头的行为注释，忽略；</li>
 *     <li>空行忽略；</li>
 *     <li>文件不存在时返回空集合，并打 warn 日志——服务照常启动，视同过滤器禁用。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocalFileSensitiveWordSource implements SensitiveWordSource {

    private final SensitiveWordProperties properties;
    private final ResourceLoader resourceLoader;

    @Override
    public Collection<String> loadWords() {
        String path = properties.getDictionaryPath();
        Resource resource = resourceLoader.getResource(path);
        if (!resource.exists()) {
            log.warn("[SensitiveWord] 字典资源不存在 path={}，敏感词过滤将退化为空字典（不拦截）", path);
            return List.of();
        }
        List<String> words = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                words.add(trimmed);
            }
        } catch (IOException e) {
            log.error("[SensitiveWord] 读取字典失败 path={}，敏感词过滤将退化为空字典", path, e);
            return List.of();
        }
        return words;
    }
}
