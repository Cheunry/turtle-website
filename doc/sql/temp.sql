ALTER TABLE `book_chapter`
    ADD COLUMN `content` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '小说章节内容' AFTER `word_count`;

UPDATE
    `book_chapter` bc
        JOIN
        `book_content` bcon
        ON
            bc.id = bcon.chapter_id
SET
    bc.content = bcon.content;

DROP TABLE IF EXISTS `book_content`;

ALTER TABLE `book_chapter`
    DROP KEY `pk_id`;

ALTER TABLE `book_chapter`
    MODIFY COLUMN `word_count` int(10) unsigned NOT NULL DEFAULT '0' COMMENT '章节字数';


SELECT id, book_name, word_count
FROM book_info
WHERE id > 0
  AND word_count > 0
ORDER BY id ASC
LIMIT 30;


-- 1. 如果 id 已经是自增主键，先去掉自增属性（为了修改主键），由于 MySQL 限制，可能需要先删主键
--    通常最简单的办法是重建表，或者按以下顺序操作：

-- A. 先把 id 改成普通列（去掉自增），同时删掉原来的主键
ALTER TABLE book_chapter MODIFY id bigint(20) unsigned NOT NULL;
ALTER TABLE book_chapter DROP PRIMARY KEY;

-- B. 此时表没有主键了。添加复合主键 (book_id, chapter_num)
--    这将触发 MySQL 重组数据，按 book_id 物理排序（耗时操作）
ALTER TABLE book_chapter ADD PRIMARY KEY (book_id, chapter_num);

-- C. 把 id 加回自增属性，并设为唯一索引
--    注意：MySQL 要求 AUTO_INCREMENT 列必须是索引的第一列（可以是主键，也可以是唯一索引）
ALTER TABLE book_chapter MODIFY id bigint(20) unsigned NOT NULL AUTO_INCREMENT, ADD UNIQUE KEY uk_id (id);

UPDATE book_chapter
SET chapter_num = chapter_num + 1
ORDER BY chapter_num DESC;



ALTER TABLE `news_info`
    ADD COLUMN `content` MEDIUMTEXT NOT NULL COMMENT '新闻内容' AFTER `title`;

UPDATE `news_info` AS ni
    JOIN `news_content` AS nc ON ni.id = nc.news_id
SET
    ni.content = nc.content,
    -- 可选：更新 news_info 表的更新时间
    ni.update_time = NOW();

DROP TABLE IF EXISTS `news_content`;

Drop table if exists `test`


-- 为 book_info 表添加审核字段
ALTER TABLE `book_info`
    ADD COLUMN `audit_status` tinyint(3) unsigned NOT NULL DEFAULT '0' COMMENT '审核状态;0-待审核 1-审核通过 2-审核不通过';

-- 在 book_info 表中添加审核不通过原因字段
ALTER TABLE `book_info`
    ADD COLUMN `audit_reason` varchar(500) DEFAULT NULL
        COMMENT '审核不通过原因' AFTER `audit_status`;

-- 为 book_chapter 表添加审核字段
ALTER TABLE `book_chapter`
    ADD COLUMN `audit_status` tinyint(3) unsigned NOT NULL DEFAULT '0' COMMENT '审核状态;0-待审核 1-审核通过 2-审核不通过';

-- 在 book_chapter 表中添加审核不通过原因字段
ALTER TABLE `book_chapter`
    ADD COLUMN `audit_reason` varchar(500) DEFAULT NULL
        COMMENT '审核不通过原因' AFTER `audit_status`;

update book_chapter
set audit_status = 1 where create_time < '2023-05-01';


CREATE TABLE `message_content` (
                                   `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                   `title` VARCHAR(150) NOT NULL COMMENT '消息标题',
                                   `content` TEXT NOT NULL COMMENT '消息正文',
                                   `type` TINYINT NOT NULL DEFAULT 1 COMMENT '消息类型 (0:系统公告, 1:业务提醒, 2:私信)',

    -- 新增：发送者类型，防止ID冲突
                                   `sender_type` TINYINT NOT NULL DEFAULT 0 COMMENT '发送者类型 (0:系统, 1:用户, 2:作者)',
                                   `sender_id` BIGINT NOT NULL DEFAULT 0 COMMENT '发送者ID (0表示系统)',

                                   `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                   PRIMARY KEY (`id`),
                                   INDEX `idx_sender` (`sender_id`, `sender_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息内容表';

CREATE TABLE `message_receive` (
                                   `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                   `message_id` BIGINT UNSIGNED NOT NULL COMMENT '关联内容表ID',

    -- 新增：接收者类型，彻底解决作者与用户ID冲突问题
                                   `receiver_type` TINYINT NOT NULL DEFAULT 1 COMMENT '接收者类型 (1:用户, 2:作者)',
                                   `receiver_id` BIGINT UNSIGNED NOT NULL COMMENT '接收者ID',

                                   `is_read` TINYINT NOT NULL DEFAULT 0 COMMENT '阅读状态 (0:未读, 1:已读)',
                                   `read_time` DATETIME DEFAULT NULL COMMENT '阅读时间',

    -- 新增：逻辑删除，用户删除了信箱消息，不影响原内容
                                   `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除 (0:正常, 1:已删除)',

                                   PRIMARY KEY (`id`),
    -- 核心联合索引修改：查询时必须带上 receiver_type
                                   INDEX `idx_receiver_read` (`receiver_id`, `receiver_type`, `is_read`),
                                   INDEX `idx_message_id` (`message_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息接收关联表';