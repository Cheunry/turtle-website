package com.novel.ai.config;

import com.novel.ai.tool.AuditTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 将 {@link AuditTools} 上的 {@code @Tool} 方法注册为 {@link ToolCallbackProvider}，
 * 供 Spring AI MCP Server 自动装配（见 {@code McpServerAutoConfiguration#mcpSyncServer}）。
 * <p>
 * <b>为何单独配置？</b>
 * {@link org.springframework.ai.chat.client.ChatClient} 的 {@code defaultTools(Object)}
 * 与 MCP 侧的工具发现是两条链路：前者在构建 ChatClient 时已显式传入 {@code AuditTools}；
 * 后者依赖容器中的 {@code ToolCallbackProvider} Bean 来收集 {@code @Tool} 元数据。
 * 本配置类把两条链路指向同一套 {@code AuditTools} 实例，避免重复定义方法。
 */
@Configuration
public class McpToolRegistrationConfig {

    @Bean
    public ToolCallbackProvider auditToolsCallbackProvider(AuditTools auditTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(auditTools)
                .build();
    }
}
