package com.novel.ai.mq;

/**
 * novel-ai 模块自有 MQ 常量。<b>暂不下沉到 novel-common.AmqpConsts</b>——
 * 因为目前 topic 生产消费都在 ai 自治范围内，下沉会让 common 承担不必要的 topic 定义。
 * 将来人审后台正式上线，再把 {@link HumanReviewTaskMq} 这部分迁移到 AmqpConsts 供消费方使用。
 */
public final class AiMqConsts {

    private AiMqConsts() {
    }

    /**
     * 人审工单：Agent 调用 {@code escalateToHuman} Tool 时落工单。
     * <p>
     * <b>设计要点</b>：
     * <ul>
     *     <li>独立 topic，与业务审核结果 topic 解耦——消费方是"人审后台"，不是 book 服务；</li>
     *     <li>consumer group 留给消费方自定义，生产者侧不感知；</li>
     *     <li>tag 粒度区分来源（书籍 / 章节），便于人审后台筛选队列。</li>
     * </ul>
     */
    public static final class HumanReviewTaskMq {
        public static final String TOPIC = "topic-ai-human-review-task";
        public static final String TAG_FROM_BOOK = "from_book";
        public static final String TAG_FROM_CHAPTER = "from_chapter";
        public static final String TAG_FROM_AGENT_TOOL = "from_agent_tool";

        private HumanReviewTaskMq() {
        }
    }
}
