package com.novel.ai.controller.front;

import com.alibaba.cloud.ai.dashscope.image.DashScopeImageOptions;
import com.novel.common.constant.ApiRouterConsts;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.web.bind.annotation.*;

@Tag(name = "FrontAiController", description = "前台门户-AI模块")
@RestController
@RequestMapping(ApiRouterConsts.API_FRONT_AI_URL_PREFIX)
@RequiredArgsConstructor
public class ChatController {

    private final ChatClient chatClient;
    private final ImageModel imageModel;

    @GetMapping("/chat") // 明确指定为 GET 方法
    public String chat(@RequestParam String prompt) { // 明确使用 @RequestParam 绑定查询参数
        return chatClient.prompt()
                .user(prompt)
                .call()
                .content();
    }

    @GetMapping("/image")
    public String imageAdvanced(@RequestParam String prompt) {
        ImageResponse response = imageModel.call(
                new ImagePrompt(prompt, DashScopeImageOptions.builder()
                        .withN(1)             // 生成图片张数
                        .withHeight(1472)
                        .withWidth(1140)
                        .build())
        );
        return response.getResult().getOutput().getUrl();
    }

}
