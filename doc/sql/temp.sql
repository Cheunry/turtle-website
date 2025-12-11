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