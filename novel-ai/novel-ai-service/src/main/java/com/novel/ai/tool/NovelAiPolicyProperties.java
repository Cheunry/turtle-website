package com.novel.ai.tool;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 平台内容审核政策。数据源为 Nacos 配置中心
 * （dataId={@code novel-ai-policies.yml}，group={@code DEFAULT_GROUP}），
 * 运营人员可直接在 Nacos 控制台修改 → 自动热更新（{@code @RefreshScope}）。
 * <p>
 * <b>为什么放在 Nacos 而不是 classpath yaml？</b>
 * <ul>
 *     <li>违禁词 / 监管政策变化比较频繁，不能每次都走发布流程；</li>
 *     <li>多实例部署时天然一致；</li>
 *     <li>审计留痕（Nacos 控制台有历史版本）。</li>
 * </ul>
 * <p>
 * 供 {@code AuditTools#queryPlatformPolicy(String)} 使用——模型审核到疑似违规时，
 * 可以主动查询平台对某个主题（暴力/色情/政治/宗教/广告/等）的明文规定作为判断依据。
 */
@Data
@RefreshScope
@ConfigurationProperties(prefix = "novel.ai.policy")
public class NovelAiPolicyProperties {

    /** 政策规则列表，按 topic 分类。 */
    private List<PolicyEntry> rules = new ArrayList<>();

    /**
     * 根据 topic 精确查询政策；未命中时返回空列表（让 Tool 决定降级）。
     */
    public List<PolicyEntry> findByTopic(String topic) {
        if (topic == null || topic.isBlank()) {
            return Collections.emptyList();
        }
        String normalized = topic.trim().toLowerCase();
        List<PolicyEntry> result = new ArrayList<>();
        for (PolicyEntry entry : rules) {
            if (entry.getTopic() != null && entry.getTopic().trim().toLowerCase().equals(normalized)) {
                result.add(entry);
            }
        }
        return result;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PolicyEntry {
        /** 主题类别：violence / porn / politics / religion / ad / privacy / other。 */
        @NotBlank
        private String topic;

        /** 严重度：low / medium / high / critical。 */
        private String severity;

        /** 政策正文（供 LLM 阅读）。 */
        @NotBlank
        private String description;

        /** 触发该政策时建议的默认处置：reject / pending / pass-with-warning。 */
        private String suggestedAction;
    }
}
