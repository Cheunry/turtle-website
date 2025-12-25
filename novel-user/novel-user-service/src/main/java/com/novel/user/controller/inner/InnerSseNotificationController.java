package com.novel.user.controller.inner;

import com.novel.common.constant.ApiRouterConsts;
import com.novel.user.service.SseNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 内部SSE通知控制器（供其他服务调用）
 * 用于推送实时通知给用户或作者
 */
@Slf4j
@RestController
@RequestMapping(ApiRouterConsts.API_INNER_USER_URL_PREFIX + "/sse")
@RequiredArgsConstructor
public class InnerSseNotificationController {

    private final SseNotificationService sseNotificationService;

    /**
     * 向用户推送通知
     * @param userId 用户ID
     * @param eventType 事件类型
     * @param data 消息数据（JSON字符串）
     * @return 是否推送成功
     */
    @PostMapping("/push/user")
    public Boolean pushToUser(
            @RequestParam Long userId,
            @RequestParam String eventType,
            @RequestBody String data) {
        boolean success = sseNotificationService.sendToUser(userId, eventType, data);
        log.debug("内部推送用户通知，userId: {}, eventType: {}, success: {}", userId, eventType, success);
        return success;
    }

    /**
     * 向作者推送通知
     * @param authorId 作者ID
     * @param eventType 事件类型
     * @param data 消息数据（JSON字符串）
     * @return 是否推送成功
     */
    @PostMapping("/push/author")
    public Boolean pushToAuthor(
            @RequestParam Long authorId,
            @RequestParam String eventType,
            @RequestBody String data) {
        boolean success = sseNotificationService.sendToAuthor(authorId, eventType, data);
        log.debug("内部推送作者通知，authorId: {}, eventType: {}, success: {}", authorId, eventType, success);
        return success;
    }
}

