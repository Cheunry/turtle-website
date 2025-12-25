package com.novel.user.controller.front;

import com.novel.common.auth.UserHolder;
import com.novel.common.constant.ApiRouterConsts;
import com.novel.common.constant.SystemConfigConsts;
import com.novel.user.service.SseNotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * SSE 通知控制器
 * 提供 Server-Sent Events 端点，用于前端实时接收通知
 */
@Slf4j
@Tag(name = "SseNotificationController", description = "SSE实时通知")
@SecurityRequirement(name = SystemConfigConsts.HTTP_AUTH_HEADER_NAME)
@RestController
@RequestMapping(ApiRouterConsts.API_FRONT_USER_URL_PREFIX + "/sse")
@RequiredArgsConstructor
public class SseNotificationController {

    private final SseNotificationService sseNotificationService;

    /**
     * 心跳发送线程池（单线程即可）
     */
    private final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "sse-heartbeat");
        t.setDaemon(true);
        return t;
    });

    /**
     * SSE 连接超时时间（30分钟）
     */
    private static final long SSE_TIMEOUT = 30 * 60 * 1000L;

    /**
     * 心跳间隔（25秒，小于超时时间）
     */
    private static final long HEARTBEAT_INTERVAL = 25 * 1000L;

    @Operation(summary = "建立SSE连接（用户）")
    @GetMapping(value = "/user/connect", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter connectForUser() {
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            throw new IllegalStateException("用户未登录");
        }

        // 创建SSE连接，设置30分钟超时
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        
        // 添加到连接池
        sseNotificationService.addUserConnection(userId, emitter);

        // 发送初始连接成功消息
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data("{\"message\":\"SSE连接已建立\"}"));
        } catch (Exception e) {
            log.error("发送初始消息失败，userId: {}", userId, e);
        }

        // 启动心跳任务（每25秒发送一次心跳）
        scheduleHeartbeat(userId, false);

        log.info("用户SSE连接已建立，userId: {}", userId);
        return emitter;
    }

    @Operation(summary = "建立SSE连接（作者）")
    @GetMapping(value = "/author/connect", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter connectForAuthor() {
        Long authorId = UserHolder.getAuthorId();
        if (authorId == null) {
            throw new IllegalStateException("用户不是作者或未登录");
        }

        // 创建SSE连接，设置30分钟超时
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        
        // 添加到连接池
        sseNotificationService.addAuthorConnection(authorId, emitter);

        // 发送初始连接成功消息
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data("{\"message\":\"SSE连接已建立\"}"));
        } catch (Exception e) {
            log.error("发送初始消息失败，authorId: {}", authorId, e);
        }

        // 启动心跳任务（每25秒发送一次心跳）
        scheduleHeartbeat(authorId, true);

        log.info("作者SSE连接已建立，authorId: {}", authorId);
        return emitter;
    }

    /**
     * 调度心跳任务
     * @param id 用户ID或作者ID
     * @param isAuthor 是否为作者
     */
    private void scheduleHeartbeat(Long id, boolean isAuthor) {
        heartbeatExecutor.scheduleWithFixedDelay(() -> {
            try {
                sseNotificationService.sendHeartbeat(id, isAuthor);
            } catch (Exception e) {
                log.warn("发送心跳失败，id: {}, isAuthor: {}", id, isAuthor, e);
            }
        }, HEARTBEAT_INTERVAL, HEARTBEAT_INTERVAL, TimeUnit.MILLISECONDS);
    }
}

