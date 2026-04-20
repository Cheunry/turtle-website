package com.novel.book.mq;

/**
 * {@link org.apache.rocketmq.spring.core.RocketMQTemplate#sendMessageInTransaction} 的 arg：
 * 表示本地数据库事务已提交，{@code executeLocalTransaction} 仅返回提交半消息。
 */
enum BookRocketMqAfterCommitMarker {
    INSTANCE
}
