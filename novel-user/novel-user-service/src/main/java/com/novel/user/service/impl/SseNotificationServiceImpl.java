package com.novel.user.service.impl;

import com.novel.user.service.SseNotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * SSE 通知服务实现类
 * 管理用户的 SSE 连接，支持推送实时通知
 */
@Slf4j
@Service
public class SseNotificationServiceImpl implements SseNotificationService {

    /**
     * 存储用户ID到SSE连接的映射
     * Key: userId（Long类型）
     * Value: SseEmitter实例
     */
    private final Map<Long, SseEmitter> userConnections = new ConcurrentHashMap<>();

    /**
     * 存储作者ID到SSE连接的映射
     * Key: authorId（Long类型）
     * Value: SseEmitter实例
     */
    private final Map<Long, SseEmitter> authorConnections = new ConcurrentHashMap<>();

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

    @Override
    public void addUserConnection(Long userId, SseEmitter emitter) {
        // 如果用户已有连接，先关闭旧连接
        SseEmitter oldEmitter = userConnections.remove(userId);
        if (oldEmitter != null) {
            try {
                oldEmitter.complete();
            } catch (Exception e) {
                log.warn("关闭旧连接失败，userId: {}", userId, e);
            }
        }

        // 添加新连接
        userConnections.put(userId, emitter);

        // 设置连接完成回调
        emitter.onCompletion(() -> {
            userConnections.remove(userId);
            log.info("用户SSE连接已关闭，userId: {}", userId);
        });

        // 设置连接超时回调
        emitter.onTimeout(() -> {
            userConnections.remove(userId);
            try {
                emitter.complete();
            } catch (Exception e) {
                log.warn("超时关闭连接失败，userId: {}", userId, e);
            }
            log.info("用户SSE连接已超时，userId: {}", userId);
        });

        // 设置错误回调
        emitter.onError((ex) -> {
            userConnections.remove(userId);
            log.error("用户SSE连接发生错误，userId: {}", userId, ex);
        });
    }

    @Override
    public void addAuthorConnection(Long authorId, SseEmitter emitter) {
        // 如果作者已有连接，先关闭旧连接
        SseEmitter oldEmitter = authorConnections.remove(authorId);
        if (oldEmitter != null) {
            try {
                oldEmitter.complete();
            } catch (Exception e) {
                log.warn("关闭旧连接失败，authorId: {}", authorId, e);
            }
        }

        // 添加新连接
        authorConnections.put(authorId, emitter);

        // 设置连接完成回调
        emitter.onCompletion(() -> {
            authorConnections.remove(authorId);
            log.info("作者SSE连接已关闭，authorId: {}", authorId);
        });

        // 设置连接超时回调
        emitter.onTimeout(() -> {
            authorConnections.remove(authorId);
            try {
                emitter.complete();
            } catch (Exception e) {
                log.warn("超时关闭连接失败，authorId: {}", authorId, e);
            }
            log.info("作者SSE连接已超时，authorId: {}", authorId);
        });

        // 设置错误回调
        emitter.onError((ex) -> {
            authorConnections.remove(authorId);
            log.error("作者SSE连接发生错误，authorId: {}", authorId, ex);
        });
    }

    @Override
    public void removeUserConnection(Long userId) {
        SseEmitter emitter = userConnections.remove(userId);
        if (emitter != null) {
            try {
                emitter.complete();
            } catch (Exception e) {
                log.warn("移除用户连接失败，userId: {}", userId, e);
            }
        }
    }

    @Override
    public void removeAuthorConnection(Long authorId) {
        SseEmitter emitter = authorConnections.remove(authorId);
        if (emitter != null) {
            try {
                emitter.complete();
            } catch (Exception e) {
                log.warn("移除作者连接失败，authorId: {}", authorId, e);
            }
        }
    }

    @Override
    public boolean sendToUser(Long userId, String eventType, String data) {
        SseEmitter emitter = userConnections.get(userId);
        if (emitter == null) {
            log.debug("用户未建立SSE连接，userId: {}", userId);
            return false;
        }

        try {
            emitter.send(SseEmitter.event()
                    .name(eventType)
                    .data(data));
            log.debug("SSE消息已推送给用户，userId: {}, eventType: {}", userId, eventType);
            return true;
        } catch (IOException e) {
            log.error("推送SSE消息失败，userId: {}, eventType: {}", userId, eventType, e);
            // 连接异常，移除连接
            userConnections.remove(userId);
            try {
                emitter.complete();
            } catch (Exception ex) {
                log.warn("完成异常连接失败，userId: {}", userId, ex);
            }
            return false;
        }
    }

    @Override
    public boolean sendToAuthor(Long authorId, String eventType, String data) {
        SseEmitter emitter = authorConnections.get(authorId);
        if (emitter == null) {
            log.debug("作者未建立SSE连接，authorId: {}", authorId);
            return false;
        }

        try {
            emitter.send(SseEmitter.event()
                    .name(eventType)
                    .data(data));
            log.debug("SSE消息已推送给作者，authorId: {}, eventType: {}", authorId, eventType);
            return true;
        } catch (IOException e) {
            log.error("推送SSE消息失败，authorId: {}, eventType: {}", authorId, eventType, e);
            // 连接异常，移除连接
            authorConnections.remove(authorId);
            try {
                emitter.complete();
            } catch (Exception ex) {
                log.warn("完成异常连接失败，authorId: {}", authorId, ex);
            }
            return false;
        }
    }

    @Override
    public void sendHeartbeat(Long userId, boolean isAuthor) {
        if (isAuthor) {
            sendToAuthor(userId, "heartbeat", "ping");
        } else {
            sendToUser(userId, "heartbeat", "ping");
        }
    }

    @Override
    public ConnectionStats getConnectionStats() {
        return new ConnectionStats(
                userConnections.size(),
                authorConnections.size(),
                userConnections.size() + authorConnections.size()
        );
    }

    @Override
    public SseEmitter createUserConnection(Long userId) {
        // 创建SSE连接，设置30分钟超时
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        
        // 添加到连接池
        addUserConnection(userId, emitter);

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

    @Override
    public SseEmitter createAuthorConnection(Long authorId) {
        // 创建SSE连接，设置30分钟超时
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        
        // 添加到连接池
        addAuthorConnection(authorId, emitter);

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
                sendHeartbeat(id, isAuthor);
            } catch (Exception e) {
                log.warn("发送心跳失败，id: {}, isAuthor: {}", id, isAuthor, e);
            }
        }, HEARTBEAT_INTERVAL, HEARTBEAT_INTERVAL, TimeUnit.MILLISECONDS);
    }
}

