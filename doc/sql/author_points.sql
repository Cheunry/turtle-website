-- ----------------------------
-- 作者点数系统相关表
-- ----------------------------

-- ----------------------------
-- 修改 author_info 表，添加点数字段
-- ----------------------------
ALTER TABLE `author_info` 
ADD COLUMN `free_points` int(10) unsigned NOT NULL DEFAULT '500' COMMENT '免费积分（每天重置为500点）' AFTER `status`,
ADD COLUMN `paid_points` int(10) unsigned NOT NULL DEFAULT '0' COMMENT '付费积分（永久积分，充值获得）' AFTER `free_points`,
ADD COLUMN `free_points_update_time` date DEFAULT NULL COMMENT '免费积分更新时间（用于判断是否需要重置）' AFTER `paid_points`;

-- ----------------------------
-- Table structure for author_points_consume_log
-- 作者点数消费记录表（记录所有消费记录）
-- ----------------------------
DROP TABLE IF EXISTS `author_points_consume_log`;
CREATE TABLE `author_points_consume_log` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `author_id` bigint(20) unsigned NOT NULL COMMENT '作者ID（关联author_info.id）',
  `consume_type` tinyint(3) unsigned NOT NULL COMMENT '消费类型;0-AI审核 1-AI润色 2-AI封面',
  `consume_points` int(10) unsigned NOT NULL COMMENT '消费点数',
  `points_type` tinyint(3) unsigned NOT NULL COMMENT '使用的点数类型;0-免费点数 1-永久积分',
  `related_id` bigint(20) unsigned DEFAULT NULL COMMENT '关联ID（如：章节ID、小说ID等，根据消费类型不同而不同）',
  `related_desc` varchar(255) DEFAULT NULL COMMENT '关联描述（如：章节名、小说名等）',
  `consume_date` date NOT NULL COMMENT '消费日期（用于统计和查询）',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `pk_id` (`id`),
  KEY `idx_authorId` (`author_id`),
  KEY `idx_consumeType` (`consume_type`),
  KEY `idx_consumeDate` (`consume_date`),
  KEY `idx_authorId_consumeDate` (`author_id`, `consume_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='作者点数消费记录表';

-- ----------------------------
-- Table structure for author_points_recharge_log
-- 作者点数充值记录表（记录充值记录）
-- ----------------------------
DROP TABLE IF EXISTS `author_points_recharge_log`;
CREATE TABLE `author_points_recharge_log` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `author_id` bigint(20) unsigned NOT NULL COMMENT '作者ID（关联author_info.id）',
  `recharge_amount` int(10) unsigned NOT NULL COMMENT '充值金额;单位：分',
  `recharge_points` int(10) unsigned NOT NULL COMMENT '充值获得的永久积分',
  `pay_channel` tinyint(3) unsigned NOT NULL DEFAULT '1' COMMENT '充值方式;0-支付宝 1-微信',
  `out_trade_no` varchar(64) NOT NULL COMMENT '商户订单号',
  `trade_no` varchar(64) DEFAULT NULL COMMENT '第三方交易号（支付宝/微信交易号）',
  `recharge_status` tinyint(3) unsigned NOT NULL DEFAULT '0' COMMENT '充值状态;0-待支付 1-支付成功 2-支付失败',
  `recharge_time` datetime DEFAULT NULL COMMENT '充值完成时间',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_outTradeNo` (`out_trade_no`),
  UNIQUE KEY `pk_id` (`id`),
  KEY `idx_authorId` (`author_id`),
  KEY `idx_rechargeStatus` (`recharge_status`),
  KEY `idx_rechargeTime` (`recharge_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='作者点数充值记录表';

