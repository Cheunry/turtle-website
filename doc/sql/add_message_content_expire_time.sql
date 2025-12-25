-- 为 message_content 表添加过期时间字段
-- 如果字段已存在，执行会报错，可以忽略

ALTER TABLE `message_content` 
ADD COLUMN `expire_time` DATETIME DEFAULT NULL COMMENT '消息/链接过期时间(NULL表示永不过期)' AFTER `extension`;

-- 添加索引（可选，如果经常按过期时间查询）
-- ALTER TABLE `message_content` ADD INDEX `idx_expire_time` (`expire_time`);

