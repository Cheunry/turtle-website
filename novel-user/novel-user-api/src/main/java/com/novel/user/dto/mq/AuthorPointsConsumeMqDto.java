package com.novel.user.dto.mq;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 作者积分消费 MQ 消息 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthorPointsConsumeMqDto {

    /**
     * 作者ID
     */
    private Long authorId;

    /**
     * 消费类型;0-AI审核 1-AI润色 2-AI封面
     */
    private Integer consumeType;

    /**
     * 消费总点数
     */
    private Integer consumePoints;

    /**
     * 使用的免费积分数
     */
    private Integer usedFreePoints;

    /**
     * 使用的付费积分数
     */
    private Integer usedPaidPoints;

    /**
     * 关联ID（如：章节ID、小说ID等）
     */
    private Long relatedId;

    /**
     * 关联描述（如：章节名、小说名等）
     */
    private String relatedDesc;

    /**
     * 消费日期
     */
    private LocalDate consumeDate;

    /**
     * 幂等性唯一标识（用于防止重复扣分）
     */
    private String idempotentKey;

}

