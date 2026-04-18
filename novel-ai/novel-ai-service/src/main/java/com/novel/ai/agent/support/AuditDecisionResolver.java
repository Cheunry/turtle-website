package com.novel.ai.agent.support;

import com.novel.ai.model.AuditDecisionAiOutput;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 把 LLM 返回的 {@link AuditDecisionAiOutput} 转成落库所需的三个字段，
 * 对缺失或越界的值做兜底，避免上游拿到奇怪的数字。
 */
@Component
public class AuditDecisionResolver {

    /** auditStatus 缺失时兜底 0（待审核），避免误判为通过。 */
    public Integer resolveStatus(AuditDecisionAiOutput output) {
        if (output == null || output.auditStatus() == null) {
            return 0;
        }
        return output.auditStatus();
    }

    /** aiConfidence 缺失默认 0.5，clamp 到 [0,1]，保留两位小数。 */
    public BigDecimal resolveConfidence(AuditDecisionAiOutput output) {
        double raw = (output != null && output.aiConfidence() != null) ? output.aiConfidence() : 0.5;
        if (raw < 0) {
            raw = 0;
        } else if (raw > 1) {
            raw = 1;
        }
        return BigDecimal.valueOf(raw).setScale(2, RoundingMode.HALF_UP);
    }

    /** auditReason 为空时兜底为固定文案。 */
    public String resolveReason(AuditDecisionAiOutput output) {
        if (output == null || output.auditReason() == null || output.auditReason().trim().isEmpty()) {
            return "AI审核完成";
        }
        return output.auditReason();
    }
}
