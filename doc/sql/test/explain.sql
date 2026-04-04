

explain  select book_chapter.content from book_chapter where id = 1334318184854036480 = 1;-- 1
explain  select book_chapter.content from book_chapter where id = 1334318184854036480 = 1 and  audit_status = 1; -- 2

-- 按 book_id + chapter_num 查询单章内容（需带上 audit_status=1）
explain select book_chapter.content from book_chapter where book_id = 1334318182169681920 and chapter_num = 1;  -- 3
explain select book_chapter.content from book_chapter where book_id = 1334318182169681920 and chapter_num = 1 and audit_status = 1; -- 4

-- 查询某书籍的所有审核通过章节（书籍目录）
explain
SELECT id, book_id, chapter_num, chapter_name, word_count, update_time, is_vip, audit_status
FROM book_chapter
WHERE book_id = 1334328310788882432 AND audit_status = 1
ORDER BY chapter_num ASC;
-- 5 未创建索引（bookid，chapterid，audit_status）
-- 6 创建了索引（bookid，chapterid，audit_status）
-- 7 创建了索引(book_id, audit_status, chapter_num)

-- 只查这几个字段，看 explain
explain
SELECT book_id, audit_status, chapter_num
FROM book_chapter
WHERE book_id = 1334328310788882432 AND audit_status = 1
ORDER BY chapter_num ASC;
-- 8

explain
SELECT book_id, audit_status, chapter_num
FROM book_chapter FORCE INDEX (book_chapter_audit) -- 这里填你新索引的名字
WHERE book_id = 1334328310788882432 AND audit_status = 1
ORDER BY chapter_num ASC;
-- 9

-- 最多访问榜 (Top 10)
explain
SELECT book_name, author_name, visit_count
FROM book_info
WHERE audit_status = 1
ORDER BY visit_count DESC
LIMIT 10;
-- id：10

-- 最近更新榜
explain
SELECT book_name, author_name, update_time
FROM book_info
WHERE audit_status = 1
ORDER BY update_time DESC
LIMIT 10;
-- id：11

-- 查询上一章
explain
SELECT * FROM book_chapter
WHERE book_id = 1334328310788882432 AND chapter_num < 4 AND audit_status = 1
ORDER BY chapter_num DESC LIMIT 1;
-- 12

-- 查询下一章
explain
SELECT * FROM book_chapter
WHERE book_id = 1334328310788882432 AND chapter_num > 4 AND audit_status = 1
ORDER BY chapter_num ASC LIMIT 1;
-- 13

-- 查询每本书的第一章编号
explain
SELECT book_id, MIN(chapter_num) AS first_chapter_num
FROM book_chapter
WHERE audit_status = 1 AND book_id IN (1334328310788882432)
GROUP BY book_id;
-- 14

-- 按 ID 查询书籍基本信息
explain
SELECT * FROM book_info WHERE id = 1334328310788882432;
-- 15

-- 搜索书籍（多条件 + 关键词模糊搜索）
explain
SELECT id, category_id, category_name, book_name, author_id, author_name,
       word_count, last_chapter_name, visit_count, book_status, audit_status
FROM book_info
WHERE word_count > 0
  AND (book_name LIKE '%爱%' AND author_name LIKE '%xi%')
ORDER BY last_chapter_update_time DESC;
-- 16

-- 查询作者的所有书籍
explain
SELECT * FROM book_info IGNORE INDEX (idx_author_book)
WHERE author_name = 'xixi' AND audit_status = 1
ORDER BY create_time DESC;
-- 17-1 强制忽略索引idx_author_book (author_name, audit_status, create_time DESC)

explain
SELECT * FROM book_info
WHERE author_name = 'xixi' AND audit_status = 1
ORDER BY create_time DESC;
-- 17-2

-- 更新书籍访问量
explain
UPDATE book_info SET visit_count = visit_count + 1 WHERE id = 1334328310788882432;
-- 18


  -- 查询用户的书架书籍
explain
SELECT * FROM user_bookshelf WHERE user_id = 7;
-- 19

