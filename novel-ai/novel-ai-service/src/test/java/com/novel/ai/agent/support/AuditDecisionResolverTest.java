package com.novel.ai.agent.support;

import com.novel.ai.model.AuditDecisionAiOutput;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class AuditDecisionResolverTest {

    private final AuditDecisionResolver resolver = new AuditDecisionResolver();

    @Test
    void status_defaults_to_pending_when_output_or_field_missing() {
        assertThat(resolver.resolveStatus(null)).isEqualTo(0);
        assertThat(resolver.resolveStatus(new AuditDecisionAiOutput(null, 0.9, "ok"))).isEqualTo(0);
    }

    @Test
    void status_returns_field_when_present() {
        assertThat(resolver.resolveStatus(new AuditDecisionAiOutput(1, 0.9, "ok"))).isEqualTo(1);
        assertThat(resolver.resolveStatus(new AuditDecisionAiOutput(2, 0.1, "bad"))).isEqualTo(2);
    }

    @Test
    void confidence_defaults_to_half_when_missing() {
        assertThat(resolver.resolveConfidence(null)).isEqualTo(new BigDecimal("0.50"));
        assertThat(resolver.resolveConfidence(new AuditDecisionAiOutput(1, null, "r")))
                .isEqualTo(new BigDecimal("0.50"));
    }

    @Test
    void confidence_clamped_to_valid_range_and_two_decimals() {
        assertThat(resolver.resolveConfidence(new AuditDecisionAiOutput(1, -0.3, "r")))
                .isEqualTo(new BigDecimal("0.00"));
        assertThat(resolver.resolveConfidence(new AuditDecisionAiOutput(1, 1.7, "r")))
                .isEqualTo(new BigDecimal("1.00"));
        assertThat(resolver.resolveConfidence(new AuditDecisionAiOutput(1, 0.876, "r")))
                .isEqualTo(new BigDecimal("0.88"));
    }

    @Test
    void reason_falls_back_when_blank() {
        assertThat(resolver.resolveReason(null)).isEqualTo("AI审核完成");
        assertThat(resolver.resolveReason(new AuditDecisionAiOutput(1, 0.9, null))).isEqualTo("AI审核完成");
        assertThat(resolver.resolveReason(new AuditDecisionAiOutput(1, 0.9, "   "))).isEqualTo("AI审核完成");
    }

    @Test
    void reason_returns_trimmed_value_when_present() {
        assertThat(resolver.resolveReason(new AuditDecisionAiOutput(1, 0.9, "合规")))
                .isEqualTo("合规");
    }
}
