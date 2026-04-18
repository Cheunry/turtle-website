package com.novel.ai.agent.core;

import org.junit.jupiter.api.Test;
import org.springframework.ai.retry.NonTransientAiException;

import static org.assertj.core.api.Assertions.assertThat;

class AuditErrorClassifierTest {

    private final AuditErrorClassifier classifier = new AuditErrorClassifier();

    @Test
    void null_exception_is_not_inspection_failure() {
        assertThat(classifier.isContentInspectionFailed(null)).isFalse();
        assertThat(classifier.classify(null)).isEqualTo(AuditErrorCategory.OTHER);
    }

    @Test
    void non_transient_with_data_inspection_keyword_is_content_inspection() {
        Exception e = new NonTransientAiException("DataInspectionFailed: risky content");
        assertThat(classifier.isContentInspectionFailed(e)).isTrue();
        assertThat(classifier.classify(e)).isEqualTo(AuditErrorCategory.CONTENT_INSPECTION_FAILED);
    }

    @Test
    void non_transient_without_keyword_is_not_inspection() {
        Exception e = new NonTransientAiException("invalid api key");
        assertThat(classifier.isContentInspectionFailed(e)).isFalse();
        assertThat(classifier.classify(e)).isEqualTo(AuditErrorCategory.OTHER);
    }

    @Test
    void generic_exception_with_inappropriate_content_is_inspection() {
        Exception e = new RuntimeException("inappropriate content detected by model");
        assertThat(classifier.isContentInspectionFailed(e)).isTrue();
    }

    @Test
    void generic_exception_with_chinese_keyword_is_inspection() {
        assertThat(classifier.isContentInspectionFailed(new RuntimeException("命中不当内容"))).isTrue();
        assertThat(classifier.isContentInspectionFailed(new RuntimeException("内容安全检查未通过"))).isTrue();
    }

    @Test
    void unrelated_exception_is_not_inspection() {
        assertThat(classifier.isContentInspectionFailed(new RuntimeException("connection timeout"))).isFalse();
    }
}
