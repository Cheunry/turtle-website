package com.novel.common.constant;

/**
 * AMQP/MQ 相关常量
 */
public class AmqpConsts {

    /**
     * 小说信息改变 MQ
     */
    public static class BookChangeMq {

        /**
         * 小说信息改变 Topic
         */
        public static final String TOPIC = "topic-book-change";
        
        /**
         * 消息 Tag: 用于区分是哪种类型的改变（例如只是点击量更新，还是内容更新）
         * 目前可以统一处理，也可以分 Tag
         */
        public static final String TAG_UPDATE = "update";

        /**
         * 消息 Tag: 章节更新（用于推送通知）
         */
        public static final String TAG_CHAPTER_UPDATE = "chapter_update";

        /**
         * 消费者组 - ES 同步
         */
        public static final String CONSUMER_GROUP_ES = "group-book-es-sync";

    }

    /**
     * 书籍信息更新 MQ（异步更新书籍字数、最新章节等信息）
     */
    public static class BookInfoUpdateMq {

        /**
         * 书籍信息更新 Topic
         */
        public static final String TOPIC = "topic-book-info-update";

        /**
         * 书籍信息更新 Tag
         */
        public static final String TAG_UPDATE_INFO = "update_info";

        /**
         * 消费者组 - 异步更新书籍信息
         */
        public static final String CONSUMER_GROUP_UPDATE_INFO = "group-book-info-update";

    }

    /**
     * 小说审核 MQ
     */
    public static class BookAuditMq {

        /**
         * 小说审核 Topic
         */
        public static final String TOPIC = "topic-book-audit";

        /**
         * 审核小说 Tag
         */
        public static final String TAG_AUDIT_BOOK = "audit_book";

        /**
         * 审核章节 Tag
         */
        public static final String TAG_AUDIT_CHAPTER = "audit_chapter";

        /**
         * 消费者组 - 审核小说
         */
        public static final String CONSUMER_GROUP_AUDIT_BOOK = "group-audit-book";

        /**
         * 消费者组 - 审核章节
         */
        public static final String CONSUMER_GROUP_AUDIT_CHAPTER = "group-audit-chapter";

    }

    /**
     * 小说审核请求 MQ（业务服务 -> AI服务）
     */
    public static class BookAuditRequestMq {

        /**
         * 审核请求 Topic
         */
        public static final String TOPIC = "topic-book-audit-request";

        /**
         * 审核书籍请求 Tag
         */
        public static final String TAG_AUDIT_BOOK_REQUEST = "audit_book_request";

        /**
         * 审核章节请求 Tag
         */
        public static final String TAG_AUDIT_CHAPTER_REQUEST = "audit_chapter_request";

        /**
         * 消费者组 - AI服务处理书籍审核请求
         */
        public static final String CONSUMER_GROUP_AUDIT_BOOK_REQUEST = "group-ai-audit-book-request";

        /**
         * 消费者组 - AI服务处理章节审核请求
         */
        public static final String CONSUMER_GROUP_AUDIT_CHAPTER_REQUEST = "group-ai-audit-chapter-request";

    }

    /**
     * 小说审核结果 MQ（AI服务 -> 业务服务）
     */
    public static class BookAuditResultMq {

        /**
         * 审核结果 Topic
         */
        public static final String TOPIC = "topic-book-audit-result";

        /**
         * 书籍审核结果 Tag
         */
        public static final String TAG_AUDIT_BOOK_RESULT = "audit_book_result";

        /**
         * 章节审核结果 Tag
         */
        public static final String TAG_AUDIT_CHAPTER_RESULT = "audit_chapter_result";

        /**
         * 消费者组 - 业务服务处理书籍审核结果
         */
        public static final String CONSUMER_GROUP_AUDIT_BOOK_RESULT = "group-book-audit-book-result";

        /**
         * 消费者组 - 业务服务处理章节审核结果
         */
        public static final String CONSUMER_GROUP_AUDIT_CHAPTER_RESULT = "group-book-audit-chapter-result";

    }

    /**
     * 章节提交 MQ（网关 -> 业务服务，用于完全异步化章节更新）
     */
    public static class ChapterSubmitMq {

        /**
         * 章节提交 Topic
         */
        public static final String TOPIC = "topic-chapter-submit";

        /**
         * 章节提交 Tag
         */
        public static final String TAG_SUBMIT = "submit";

        /**
         * 消费者组 - 处理章节提交
         */
        public static final String CONSUMER_GROUP_SUBMIT = "group-chapter-submit";

    }

    /**
     * 书籍更新 MQ（网关 -> 业务服务，用于完全异步化书籍更新）
     */
    public static class BookUpdateMq {

        /**
         * 书籍更新 Topic
         */
        public static final String TOPIC = "topic-book-update";

        /**
         * 书籍更新 Tag
         */
        public static final String TAG_UPDATE = "update";

        /**
         * 消费者组 - 处理书籍更新
         */
        public static final String CONSUMER_GROUP_UPDATE = "group-book-update";

    }

    /**
     * 书籍新增 MQ（网关 -> 业务服务，用于完全异步化书籍新增）
     */
    public static class BookAddMq {

        /**
         * 书籍新增 Topic
         */
        public static final String TOPIC = "topic-book-add";

        /**
         * 书籍新增 Tag
         */
        public static final String TAG_ADD = "add";

        /**
         * 消费者组 - 处理书籍新增
         */
        public static final String CONSUMER_GROUP_ADD = "group-book-add";

    }

    /**
     * 作者积分消费 MQ
     */
    public static class AuthorPointsConsumeMq {

        /**
         * 作者积分消费 Topic
         */
        public static final String TOPIC = "topic-author-points-consume";

        /**
         * 扣除积分 Tag
         */
        public static final String TAG_DEDUCT = "deduct";

        /**
         * 回滚积分 Tag
         */
        public static final String TAG_ROLLBACK = "rollback";

        /**
         * 消费者组 - 持久化积分消费记录
         */
        public static final String CONSUMER_GROUP_PERSIST = "group-author-points-persist";

        /**
         * 消费者组 - 持久化积分回滚记录
         */
        public static final String CONSUMER_GROUP_ROLLBACK = "group-author-points-rollback";

    }
}
