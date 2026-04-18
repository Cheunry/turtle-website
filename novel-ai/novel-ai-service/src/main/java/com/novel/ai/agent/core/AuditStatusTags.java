package com.novel.ai.agent.core;

/**
 * 把业务审核状态码统一成 Micrometer 标签值，保证指标维度一致。
 * <ul>
 *     <li>0 → {@code pending}</li>
 *     <li>1 → {@code passed}</li>
 *     <li>2 → {@code rejected}</li>
 *     <li>null / 其他 → {@code unknown}</li>
 * </ul>
 */
public final class AuditStatusTags {

    public static final String PENDING = "pending";
    public static final String PASSED = "passed";
    public static final String REJECTED = "rejected";
    public static final String UNKNOWN = "unknown";

    private AuditStatusTags() {}

    public static String of(Integer auditStatus) {
        if (auditStatus == null) {
            return UNKNOWN;
        }
        return switch (auditStatus) {
            case 0 -> PENDING;
            case 1 -> PASSED;
            case 2 -> REJECTED;
            default -> UNKNOWN;
        };
    }
}
