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
         * 消费者组 - ES 同步
         */
        public static final String CONSUMER_GROUP_ES = "group-book-es-sync";

    }
}
