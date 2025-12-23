-- 全员消息已读记录表（读扩散模式）
-- 用于记录用户对 type=0 的全员系统通知的已读状态
CREATE TABLE `message_broadcast_read` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `message_id` BIGINT UNSIGNED NOT NULL COMMENT '消息内容ID（关联 message_content.id）',
  `receiver_id` BIGINT UNSIGNED NOT NULL COMMENT '接收者ID (对应UserID或AuthorID)',
  `receiver_type` TINYINT NOT NULL DEFAULT 1 COMMENT '接收者身份类型 (1:普通用户/UserID, 2:作者/AuthorID)',
  `read_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '阅读时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_message_receiver` (`message_id`, `receiver_id`, `receiver_type`) COMMENT '确保同一用户对同一消息只有一条已读记录',
  INDEX `idx_receiver` (`receiver_type`, `receiver_id`) COMMENT '方便查询用户的已读记录'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='全员消息已读记录表（读扩散）';

