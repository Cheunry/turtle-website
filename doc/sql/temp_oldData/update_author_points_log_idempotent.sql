-- ----------------------------
-- 补丁：author_points_consume_log 表添加 idempotent_key 字段
-- ----------------------------

ALTER TABLE `author_points_consume_log` 
ADD COLUMN `idempotent_key` varchar(64) DEFAULT NULL COMMENT '幂等性Key（MQ消费去重）';

-- 添加唯一索引
ALTER TABLE `author_points_consume_log` 
ADD UNIQUE INDEX `uk_idempotentKey` (`idempotent_key`);

