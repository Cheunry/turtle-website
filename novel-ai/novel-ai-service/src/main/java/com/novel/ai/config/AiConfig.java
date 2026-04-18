package com.novel.ai.config;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.image.DashScopeImageModel;
import com.novel.ai.advisor.NovelAiAdvisorProperties;
import com.novel.ai.advisor.RetryTransientAiAdvisor;
import com.novel.ai.advisor.StructuredOutputLogAdvisor;
import com.novel.ai.tool.AuditTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.image.ImageModel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 相关核心 Bean 装配。
 * <p>
 * 重点在 {@link ChatClient} 的构建：通过 {@code defaultAdvisors} 注册全局 Advisor 链：
 * <ol>
 *     <li>{@link RetryTransientAiAdvisor}——模型通信层瞬时错误重试（外层）；</li>
 *     <li>{@link StructuredOutputLogAdvisor}——单次调用耗时 / token / SkyWalking 埋点（内层）；</li>
 *     <li>{@link SimpleLoggerAdvisor}——Spring AI 官方 DEBUG 级 request/response 原文日志。</li>
 * </ol>
 * Spring AI 1.0 的 Observation 会自动产出 {@code gen_ai.client.*} 标准指标，无需手动 wire。
 */
@Configuration
@EnableConfigurationProperties(NovelAiAdvisorProperties.class)
public class AiConfig {

    @Bean
    public RetryTransientAiAdvisor retryTransientAiAdvisor(NovelAiAdvisorProperties props) {
        return new RetryTransientAiAdvisor(props);
    }

    @Bean
    public StructuredOutputLogAdvisor structuredOutputLogAdvisor(NovelAiAdvisorProperties props) {
        return new StructuredOutputLogAdvisor(props);
    }

    /**
     * 聊天模型全局客户端——默认挂载：
     * <ul>
     *     <li>"两级重试 + 日志/观测"Advisor 链；</li>
     *     <li>{@link AuditTools} 作为 {@code defaultTools}，让模型可在 ReAct 循环里
     *         自主调用 6 个审核相关 Function（政策查询、相似判例、敏感词扫描、
     *         人审升级、经验沉淀等）；</li>
     * </ul>
     * <p>
     * <b>和结构化输出的兼容性</b>：Spring AI 会先处理 tool_call 循环，拿到最终
     * 文本响应后再走 {@code BeanOutputConverter}，两者互不冲突；Prompt 模板同步
     * 在 system 段落里告知"工具使用规范"，避免模型把 Tool 当"常规输出通道"滥用。
     * <p>
     * 业务调用点无需手动 {@code .advisors(...)}/{@code .tools(...)}，直接
     * {@code chatClient.prompt().system().user().call()} 即可获得 advisor+tools 能力。
     */
    @Bean
    public ChatClient chatClient(DashScopeChatModel chatModel,
                                 RetryTransientAiAdvisor retryAdvisor,
                                 StructuredOutputLogAdvisor logAdvisor,
                                 AuditTools auditTools) {
        return ChatClient.builder(chatModel)
                .defaultAdvisors(retryAdvisor, logAdvisor, new SimpleLoggerAdvisor())
                .defaultTools(auditTools)
                .build();
    }

    @Bean
    public ImageModel imageModel(DashScopeImageModel dashScopeImageModel) {
        return dashScopeImageModel;
    }
}
/*
        阿里云百炼平台模型使用说明
        文本模型：https://bailian.console.aliyun.com/?tab=model#/model-market/detail/qwen3-max
        qwen-image-plus：https://bailian.console.aliyun.com/?tab=model#/model-market/detail/qwen-image-plus

        DashScope 平台容错策略
        限流：当 API 返回 Throttling 或 HTTP 429 时，已由 RetryTransientAiAdvisor 指数退避重试。
        异步任务轮询（文生图）：建议采用前密后疏的轮询节奏，设置任务超时（如 2 分钟）。
        结果持久化：图片 URL 24 小时有效期，生产需转存 OSS。
        内容安全：prompt/negative_prompt 会经过内容安全审核，违规返回 DataInspectionFailed；
                 已由 AuditErrorClassifier + BookAuditExceptionMapper 翻译为 auditStatus=2。
*/
