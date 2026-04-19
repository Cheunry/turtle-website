-- 已有库升级：书籍/章节表增加「拒审违禁词」列，与 content_audit.key_snippet 语义一致，便于按书/章节直接查询。
-- 执行前请备份。

ALTER TABLE book_info
    ADD COLUMN reject_sensitive_words varchar(500) NULL COMMENT '审核不通过时命中违禁词（顿号拼接，本地AC拦截时有值）' AFTER audit_reason;

ALTER TABLE book_chapter
    ADD COLUMN reject_sensitive_words varchar(500) NULL COMMENT '审核不通过时命中违禁词（顿号拼接，本地AC拦截时有值）' AFTER audit_reason;
