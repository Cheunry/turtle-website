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