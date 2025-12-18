package com.novel.ai.controller.front;

import com.novel.common.constant.ApiRouterConsts;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.*;

@Tag(name = "FrontAiController", description = "前台门户-AI模块")
@RestController
@RequestMapping(ApiRouterConsts.API_FRONT_AI_URL_PREFIX)
@RequiredArgsConstructor
public class ChatController {

    private final ChatClient chatClient;

    @GetMapping("/chat") // 明确指定为 GET 方法
    public String chat(@RequestParam String prompt) { // 明确使用 @RequestParam 绑定查询参数
        return chatClient.prompt()
                .user(prompt)
                .call()
                .content();
    }

}
