package com.novel.ai.mq;

import com.novel.ai.mq.dto.HumanReviewTaskMqDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 人审工单 Producer。把投递细节（topic / tag / taskId 兜底 / 异常降级）
 * 从 Tool 层抽离出来，避免在 {@code AuditTools} 里混入 MQ 细节。
 * <p>
 * <b>可靠性策略</b>：
 * <ul>
 *     <li>Producer 层<b>吞异常 + 日志告警</b>——工单投递失败不应该反向导致审核流程报错，
 *         审核还是要能把"建议进入人审"的决定写回去；</li>
 *     <li>taskId 由调用方传入时用其做幂等键，否则用 UUID 兜底（消费方自行去重）；</li>
 *     <li>使用 {@code syncSend} 获取发送状态，便于在观测平台看"工单投递成功率"指标。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HumanReviewTaskProducer {

    private final RocketMQTemplate rocketMQTemplate;

    /**
     * 投递一条人审工单。
     *
     * @param task 工单消息体；{@code taskId}/{@code createdAtMs} 为空时自动补齐
     * @param tag  {@link AiMqConsts.HumanReviewTaskMq} 中的 tag，用于人审后台按来源过滤
     * @return 实际使用的 taskId（供调用方写日志/返回给 Agent）
     */
    public String send(HumanReviewTaskMqDto task, String tag) {
        if (task == null) {
            throw new IllegalArgumentException("HumanReviewTaskMqDto 不能为 null");
        }
        if (task.getTaskId() == null || task.getTaskId().isBlank()) {
            task.setTaskId("human_review_" + UUID.randomUUID().toString().replace("-", ""));
        }
        if (task.getCreatedAtMs() == null) {
            task.setCreatedAtMs(System.currentTimeMillis());
        }

        String destination = AiMqConsts.HumanReviewTaskMq.TOPIC + ":" + tag;
        try {
            rocketMQTemplate.convertAndSend(destination, task);
            log.info("[HumanReviewTask] 工单已投递 taskId={} sourceType={} sourceId={} reason={}",
                    task.getTaskId(), task.getSourceType(), task.getSourceId(), task.getReason());
            return task.getTaskId();
        } catch (Exception e) {
            log.error("[HumanReviewTask] 工单投递失败 taskId={} destination={}",
                    task.getTaskId(), destination, e);
            return task.getTaskId();
        }
    }
}
