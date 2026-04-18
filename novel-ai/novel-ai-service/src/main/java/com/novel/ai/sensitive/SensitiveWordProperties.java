package com.novel.ai.sensitive;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 敏感词过滤配置。配置键前缀：{@code novel.ai.sensitive-filter}。
 *
 * <p>当前阶段字典从本地 classpath 文件加载；后续会接入 Nacos 配置中心做热更新，
 * 届时只需新增另一个 {@link SensitiveWordSource} 实现并切换即可。</p>
 */
@Data
@ConfigurationProperties(prefix = "novel.ai.sensitive-filter")
public class SensitiveWordProperties {

    /** 过滤器总开关，默认开启。关闭后前置 Step 会空过。 */
    private boolean enabled = true;

    /** 字典资源路径，支持 classpath:/ 前缀。默认走内置骨架文件。 */
    private String dictionaryPath = "classpath:sensitive-words.txt";

    /** 匹配时是否忽略大小写。中文无影响；英文词典开启后更保险。 */
    private boolean ignoreCase = true;
}
