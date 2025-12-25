package com.novel.user.feign;

import com.novel.common.constant.ApiRouterConsts;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * SSE通知 Feign 接口（供其他服务调用）
 */
@FeignClient(
        value = "novel-user-service",
        contextId = "sseNotificationFeign",
        path = ApiRouterConsts.API_INNER_USER_URL_PREFIX + "/sse"
)
public interface SseNotificationFeign {

    /**
     * 向用户推送通知
     * @param userId 用户ID
     * @param eventType 事件类型
     * @param data 消息数据（JSON字符串）
     * @return 是否推送成功
     */
    @PostMapping("/push/user")
    Boolean pushToUser(
            @RequestParam("userId") Long userId,
            @RequestParam("eventType") String eventType,
            @RequestBody String data
    );

    /**
     * 向作者推送通知
     * @param authorId 作者ID
     * @param eventType 事件类型
     * @param data 消息数据（JSON字符串）
     * @return 是否推送成功
     */
    @PostMapping("/push/author")
    Boolean pushToAuthor(
            @RequestParam("authorId") Long authorId,
            @RequestParam("eventType") String eventType,
            @RequestBody String data
    );
}

