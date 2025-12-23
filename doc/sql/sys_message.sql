-- 1. 消息内容表 (message_content)
CREATE TABLE `message_content` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `title` VARCHAR(150) NOT NULL COMMENT '消息标题',
  `content` TEXT COMMENT '消息正文(支持HTML)',
  
  -- 核心分类：建议细化，方便前端分栏展示
  `type` TINYINT NOT NULL DEFAULT 1 COMMENT '消息类型 (0:系统公告/全员, 1:订阅更新/追更, 2:作家助手/审核, 3:私信)',
  
  -- 业务关联：方便点击消息跳转到具体业务对象
  `link` VARCHAR(255) DEFAULT NULL COMMENT '跳转链接(如: /author/appeal?id=123)',
  `bus_id` BIGINT DEFAULT NULL COMMENT '业务ID(关联的书籍ID、章节ID等)',
  `bus_type` VARCHAR(50) DEFAULT NULL COMMENT '业务类型(如: book, chapter, author)',
  
  -- 扩展数据：存储AI封面URL、审核失败原因JSON等
  `extension` JSON DEFAULT NULL COMMENT '扩展数据(JSON)',
  
  -- 消息过期：解决临时URL有效期问题
  `expire_time` DATETIME DEFAULT NULL COMMENT '消息/链接过期时间(NULL表示永不过期)',
  
  `sender_type` TINYINT NOT NULL DEFAULT 0 COMMENT '发送者类型 (0:系统, 1:用户)',
  `sender_id` BIGINT NOT NULL DEFAULT 0 COMMENT '发送者ID',
  
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  
  PRIMARY KEY (`id`),
  INDEX `idx_sender` (`sender_id`, `sender_type`),
  INDEX `idx_create_time` (`create_time`) -- 方便清理历史日志
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息内容表';

-- 2. 接收关系表 (message_receive)
-- 采用“写扩散”模式：每发给一个用户，插入一行。
CREATE TABLE `message_receive` (
   `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
   `message_id` BIGINT UNSIGNED NOT NULL COMMENT '关联内容表ID',

   `receiver_id` BIGINT UNSIGNED NOT NULL COMMENT '接收者ID (对应UserID或AuthorID)',
   `receiver_type` TINYINT NOT NULL DEFAULT 1 COMMENT '接收者身份类型 (1:普通用户/UserID, 2:作者/AuthorID)',

   `is_read` TINYINT NOT NULL DEFAULT 0 COMMENT '阅读状态 (0:未读, 1:已读)',
   `read_time` DATETIME DEFAULT NULL COMMENT '阅读时间',
   `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '是否逻辑删除 (0:正常, 1:删除)',

   PRIMARY KEY (`id`),
   INDEX `idx_receiver_type_id` (`receiver_type`, `receiver_id`, `is_read`, `is_deleted`),
   INDEX `idx_message_id` (`message_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息接收关联表';
