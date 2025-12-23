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
