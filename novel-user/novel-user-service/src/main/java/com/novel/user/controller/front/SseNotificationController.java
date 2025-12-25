package com.novel.user.controller.front;

import com.novel.common.auth.UserHolder;
import com.novel.common.constant.ApiRouterConsts;
import com.novel.common.constant.SystemConfigConsts;
import com.novel.user.service.SseNotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 通知控制器
 * 提供 Server-Sent Events 端点，用于前端实时接收通知
 */
@Tag(name = "SseNotificationController", description = "SSE实时通知")
@SecurityRequirement(name = SystemConfigConsts.HTTP_AUTH_HEADER_NAME)
@RestController
@RequestMapping(ApiRouterConsts.API_FRONT_USER_URL_PREFIX + "/sse")
@RequiredArgsConstructor
public class SseNotificationController {

    private final SseNotificationService sseNotificationService;

    @Operation(summary = "建立SSE连接（用户）")
    @GetMapping(value = "/user/connect", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter connectForUser() {
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            throw new IllegalStateException("用户未登录");
        }
        return sseNotificationService.createUserConnection(userId);
    }

    @Operation(summary = "建立SSE连接（作者）")
    @GetMapping(value = "/author/connect", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter connectForAuthor() {
        Long authorId = UserHolder.getAuthorId();
        if (authorId == null) {
            throw new IllegalStateException("用户不是作者或未登录");
        }
        return sseNotificationService.createAuthorConnection(authorId);
    }
}

