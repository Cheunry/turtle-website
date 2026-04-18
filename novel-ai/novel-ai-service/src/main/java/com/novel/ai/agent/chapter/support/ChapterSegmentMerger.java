package com.novel.ai.agent.chapter.support;

import com.novel.book.dto.resp.ChapterAuditRespDto;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 合并分段审核结果成整章结果。合并规则（保持与原实现一致）：
 * <ol>
 *     <li>任一段"不通过"(2)→ 整章不通过；</li>
 *     <li>否则任一段"待审核"(0)→ 整章待审核；</li>
 *     <li>全部"通过"(1)→ 整章通过；</li>
 *     <li>置信度取各段均值；</li>
 *     <li>审核原因优先拼接不通过段 > 待审核段，超限截断，并在末尾补上通过段数。</li>
 * </ol>
 */
@Component
public class ChapterSegmentMerger {

    /** 数据库 audit_reason 字段限制（与原常量保持一致）。 */
    private static final int MAX_AUDIT_REASON_LENGTH = 500;

    public ChapterAuditRespDto merge(List<ChapterAuditRespDto> segmentResults,
                                     Long bookId,
                                     Integer chapterNum) {
        if (segmentResults == null || segmentResults.isEmpty()) {
            return ChapterAuditRespDto.builder()
                    .bookId(bookId)
                    .chapterNum(chapterNum)
                    .auditStatus(0)
                    .aiConfidence(new BigDecimal("0.0"))
                    .auditReason("分段审核结果为空")
                    .build();
        }

        int overallStatus = 1;
        BigDecimal totalConfidence = BigDecimal.ZERO;
        int validConfidenceCount = 0;
        List<String> failedReasons = new ArrayList<>();
        List<String> pendingReasons = new ArrayList<>();
        int passedCount = 0;

        for (int i = 0; i < segmentResults.size(); i++) {
            ChapterAuditRespDto segment = segmentResults.get(i);
            int segmentIndex = i + 1;

            Integer status = segment.getAuditStatus();
            if (status != null) {
                if (status == 2) {
                    overallStatus = 2;
                } else if (status == 0 && overallStatus == 1) {
                    overallStatus = 0;
                }
            }

            if (segment.getAiConfidence() != null) {
                totalConfidence = totalConfidence.add(segment.getAiConfidence());
                validConfidenceCount++;
            }

            String reason = segment.getAuditReason();
            boolean hasReason = reason != null && !reason.trim().isEmpty();
            if (hasReason) {
                String segmentReason = String.format("第%d段：%s", segmentIndex, reason.trim());
                if (status != null && status == 2) {
                    failedReasons.add(segmentReason);
                } else if (status != null && status == 0) {
                    pendingReasons.add(segmentReason);
                } else {
                    passedCount++;
                }
            } else if (status == null || status == 1) {
                passedCount++;
            }
        }

        BigDecimal avgConfidence = validConfidenceCount > 0
                ? totalConfidence.divide(new BigDecimal(validConfidenceCount), 2, RoundingMode.HALF_UP)
                : new BigDecimal("0.5");

        String mergedReason = buildMergedReason(
                failedReasons, pendingReasons, passedCount, segmentResults.size(), overallStatus);

        return ChapterAuditRespDto.builder()
                .bookId(bookId)
                .chapterNum(chapterNum)
                .auditStatus(overallStatus)
                .aiConfidence(avgConfidence)
                .auditReason(mergedReason)
                .build();
    }

    private String buildMergedReason(List<String> failedReasons,
                                     List<String> pendingReasons,
                                     int passedCount,
                                     int totalSegments,
                                     int overallStatus) {
        StringBuilder sb = new StringBuilder();
        appendReasons(sb, "不通过段：", failedReasons);
        if (!pendingReasons.isEmpty()) {
            if (!sb.isEmpty()) {
                sb.append(" ");
            }
            appendReasons(sb, "待审核段：", pendingReasons);
        }

        if (failedReasons.isEmpty() && pendingReasons.isEmpty() && passedCount > 0) {
            sb.append(totalSegments == 1
                    ? "内容符合网络文学平台规范，审核通过"
                    : String.format("共%d段内容，均已审核通过，符合网络文学平台规范", totalSegments));
        } else if (passedCount > 0) {
            String summary = String.format("（其余%d段通过）", passedCount);
            if (sb.length() + summary.length() <= MAX_AUDIT_REASON_LENGTH - 10) {
                sb.append(summary);
            }
        }

        String result = sb.toString().trim();
        if (result.endsWith("；")) {
            result = result.substring(0, result.length() - 1);
        }
        if (result.length() > MAX_AUDIT_REASON_LENGTH) {
            result = result.substring(0, MAX_AUDIT_REASON_LENGTH - 3) + "...";
        }
        if (result.isEmpty()) {
            result = switch (overallStatus) {
                case 1 -> "审核通过";
                case 2 -> "审核不通过";
                default -> "待审核";
            };
        }
        return result;
    }

    /** 将 reasons 以 "前缀 + 拼接；" 形式追加到 sb，超过上限则截断并提示。 */
    private void appendReasons(StringBuilder sb, String prefix, List<String> reasons) {
        if (reasons.isEmpty()) {
            return;
        }
        sb.append(prefix);
        for (String reason : reasons) {
            String toAppend = reason + "；";
            if (sb.length() + toAppend.length() > MAX_AUDIT_REASON_LENGTH - 20) {
                sb.append("（内容过长，已截断）");
                return;
            }
            sb.append(toAppend);
        }
    }
}
