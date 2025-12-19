package com.novel.ai.config;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.image.DashScopeImageModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.image.ImageModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {
    // 聊天模型 Bean
    // Spring AI 的 DashScope 实现类是 DashScopeChatModel
    @Bean
    public ChatClient chatClient(DashScopeChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .build();
    }

    // 图片生成模型 Bean
    // Spring AI 的 DashScope 实现类是 DashScopeImageModel
    @Bean
    public ImageModel imageModel(DashScopeImageModel dashScopeImageModel) {
        return dashScopeImageModel;
    }
}
/*
        阿里云百炼平台模型使用说明
        qwen3-max：https://bailian.console.aliyun.com/?spm=5176.30510405.J_bQ9d6wtWdX1_RtKN0y7Ar.1.d05159deC7bd3D&tab=model#/model-market/detail/qwen3-max
        qwen-image-plus：https://bailian.console.aliyun.com/?spm=5176.30510405.J_bQ9d6wtWdX1_RtKN0y7Ar.1.d05159deC7bd3D&tab=model#/model-market/detail/qwen-image-plus
        文本生成文档：https://bailian.console.aliyun.com/?spm=5176.30510405.J_bQ9d6wtWdX1_RtKN0y7Ar.1.d05159deC7bd3D&tab=doc#/doc/?type=model&url=2841718
        文生图文档：https://bailian.console.aliyun.com/?spm=5176.30510405.J_bQ9d6wtWdX1_RtKN0y7Ar.1.d05159deC7bd3D&tab=doc#/doc/?type=model&url=2848513

        DashScope平台的文生图容错策略
        处理限流：当 API 返回 Throttling 错误码或 HTTP 429 状态码时，表明已触发限流，限流处理请参见限流。
        异步任务轮询：轮询查询异步任务结果时，建议采用合理的轮询策略（如前30秒每3秒一次，之后拉长间隔），
        避免因过于频繁的请求而触发限流。为任务设置一个最终超时时间（如 2 分钟），超时后标记为失败。
        风险防范
        结果持久化：API 返回的图片 URL 有 24 小时有效期。生产系统必须在获取 URL 后立即下载图片，
        并转存至您自己的持久化存储服务中（如阿里云对象存储 OSS）。
        内容安全审核：所有 prompt 和 negative_prompt 都会经过内容安全审核。若输入内容不合规，
        请求将被拦截并返回 DataInspectionFailed 错误。
        生成内容的版权与合规风险：请确保您的提示词内容符合相关法律法规。
        生成包含品牌商标、名人肖像、受版权保护的 IP 形象等内容可能涉及侵权风险，请您自行评估并承担相应责任。
*/
