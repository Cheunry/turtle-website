package com.novel.common.constant;

import lombok.Getter;

/**
 * 数据库 常量
 */
public class DatabaseConsts {

    /**
     * 用户信息表
     */
    public static class UserInfoTable {

        private UserInfoTable() {
            throw new IllegalStateException(SystemConfigConsts.CONST_INSTANCE_EXCEPTION_MSG);
        }

        public static final String COLUMN_USERNAME = "username";

    }

    /**
     * 用户反馈表
     */
    public static class UserFeedBackTable {

        private UserFeedBackTable() {
            throw new IllegalStateException(SystemConfigConsts.CONST_INSTANCE_EXCEPTION_MSG);
        }

        public static final String COLUMN_USER_ID = "user_id";

    }

    /**
     * 用户书架表
     */
    public static class UserBookshelfTable {

        private UserBookshelfTable() {
            throw new IllegalStateException(SystemConfigConsts.CONST_INSTANCE_EXCEPTION_MSG);
        }

        public static final String COLUMN_USER_ID = "user_id";

        public static final String COLUMN_BOOK_ID = "book_id";

    }

    /**
     * 作家信息表
     */
    public static class AuthorInfoTable {

        private AuthorInfoTable() {
            throw new IllegalStateException(SystemConfigConsts.CONST_INSTANCE_EXCEPTION_MSG);
        }

        public static final String COLUMN_USER_ID = "user_id";

    }

    /**
     * 小说类别表
     */
    public static class BookCategoryTable {

        private BookCategoryTable() {
            throw new IllegalStateException(SystemConfigConsts.CONST_INSTANCE_EXCEPTION_MSG);
        }

        public static final String COLUMN_WORK_DIRECTION = "work_direction";

        public static final String COLUMN_SORT = "sort";

    }

    /**
     * 小说表
     */
    public static class BookTable {

        private BookTable() {
            throw new IllegalStateException(SystemConfigConsts.CONST_INSTANCE_EXCEPTION_MSG);
        }

        public static final String COLUMN_CATEGORY_ID = "category_id";

        public static final String COLUMN_BOOK_NAME = "book_name";

        public static final String AUTHOR_ID = "author_id";

        public static final String COLUMN_VISIT_COUNT = "visit_count";

        public static final String COLUMN_WORD_COUNT = "word_count";

        public static final String COLUMN_LAST_CHAPTER_UPDATE_TIME = "last_chapter_update_time";

        public static final String COLUMN_LAST_CHAPTER_UPDATE_NUM = "last_chapter_update_num";

        public static final String COLUMN_BOOK_STATUS = "book_status";      // 书籍状态;0-连载中 1-已完结

    }

    /**
     * 小说章节表
     */
    public static class BookChapterTable {

        private BookChapterTable() {
            throw new IllegalStateException(SystemConfigConsts.CONST_INSTANCE_EXCEPTION_MSG);
        }

        public static final String COLUMN_BOOK_ID = "book_id";

        public static final String COLUMN_CHAPTER_NUM = "chapter_num";

        public static final String COLUMN_CHAPTER_UPDATE_TIME = "update_time";

    }

    /**
     * 小说评论表
     */
    public static class BookCommentTable {

        private BookCommentTable() {
            throw new IllegalStateException(SystemConfigConsts.CONST_INSTANCE_EXCEPTION_MSG);
        }

        public static final String COLUMN_COMMENT_ID = "id";

        public static final String COLUMN_BOOK_ID = "book_id";

        public static final String COLUMN_USER_ID = "user_id";

    }

    /**
     * 消息接收表
     */
    public static class MessageReceiveTable {

        private MessageReceiveTable() {
            throw new IllegalStateException(SystemConfigConsts.CONST_INSTANCE_EXCEPTION_MSG);
        }

        /**
         * 接收者身份类型：普通用户/UserID
         */
        public static final Integer RECEIVER_TYPE_USER = 0;

        /**
         * 接收者身份类型：作者/AuthorID
         */
        public static final Integer RECEIVER_TYPE_AUTHOR = 1;

    }

    /**
     * 消息内容表
     */
    public static class MessageContentTable {

        private MessageContentTable() {
            throw new IllegalStateException(SystemConfigConsts.CONST_INSTANCE_EXCEPTION_MSG);
        }

        /**
         * 消息类型：系统公告/全员
         */
        public static final Integer MESSAGE_TYPE_SYSTEM_ANNOUNCEMENT = 0;

        /**
         * 消息类型：订阅更新/追更
         */
        public static final Integer MESSAGE_TYPE_SUBSCRIBE_UPDATE = 1;

        /**
         * 消息类型：作家助手/审核
         */
        public static final Integer MESSAGE_TYPE_AUTHOR_ASSISTANT = 2;

        /**
         * 消息类型：私信
         */
        public static final Integer MESSAGE_TYPE_PRIVATE_MESSAGE = 3;

        /**
         * 发送者类型：系统
         */
        public static final Integer SENDER_TYPE_SYSTEM = 0;

        /**
         * 发送者类型：用户
         */
        public static final Integer SENDER_TYPE_USER = 1;

    }

    /**
     * 通用列枚举类
     */
    @Getter
    public enum CommonColumnEnum {

        ID("id"),
        SORT("sort"),
        CREATE_TIME("create_time"),
        UPDATE_TIME("update_time");

        private String name;

        CommonColumnEnum(String name) {
            this.name = name;
        }

    }


    /**
     * SQL语句枚举类
     */
    @Getter
    public enum SqlEnum {

        LIMIT_1("limit 1"),
        LIMIT_2("limit 2"),
        LIMIT_5("limit 5"),
        LIMIT_10("limit 10"),
        LIMIT_20("limit 20"),
        LIMIT_30("limit 30"),
        LIMIT_500("limit 500");

        private String sql;

        SqlEnum(String sql) {
            this.sql = sql;
        }

        /**
         * 获取SQL语句
         * @return SQL语句
         */
        public String getSql() {
            return sql;
        }

    }

}
