package com.novel.user.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 通知服务接口
 * 管理用户的 SSE 连接，支持推送实时通知
 */
public interface SseNotificationService {

    /**
     * 创建或更新用户的SSE连接
     * @param userId 用户ID
     * @param emitter SSE连接对象
     */
    void addUserConnection(Long userId, SseEmitter emitter);

    /**
     * 创建或更新作者的SSE连接
     * @param authorId 作者ID
     * @param emitter SSE连接对象
     */
    void addAuthorConnection(Long authorId, SseEmitter emitter);

    /**
     * 移除用户连接
     * @param userId 用户ID
     */
    void removeUserConnection(Long userId);

    /**
     * 移除作者连接
     * @param authorId 作者ID
     */
    void removeAuthorConnection(Long authorId);

    /**
     * 向用户推送消息
     * @param userId 用户ID
     * @param eventType 事件类型
     * @param data 消息数据（JSON字符串）
     * @return 是否推送成功
     */
    boolean sendToUser(Long userId, String eventType, String data);

    /**
     * 向作者推送消息
     * @param authorId 作者ID
     * @param eventType 事件类型
     * @param data 消息数据（JSON字符串）
     * @return 是否推送成功
     */
    boolean sendToAuthor(Long authorId, String eventType, String data);

    /**
     * 发送心跳（保持连接活跃）
     * @param userId 用户ID或作者ID
     * @param isAuthor 是否为作者连接
     */
    void sendHeartbeat(Long userId, boolean isAuthor);

    /**
     * 获取当前连接数（用于监控）
     * @return 连接统计信息
     */
    ConnectionStats getConnectionStats();

    /**
     * 连接统计信息
     */
    record ConnectionStats(
            int userConnections,
            int authorConnections,
            int totalConnections
    ) {}
}
