package com.novel.ai.agent.support;

import com.novel.ai.dto.req.AuditExperienceSearchReqDto;
import com.novel.ai.dto.resp.AuditExperienceSearchRespDto;
import com.novel.ai.feign.SearchFeign;
import com.novel.common.resp.RestResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 封装 "RAG 检索相似审核经验" 的调用：向量检索失败时降级为空串，避免
 * 污染业务主流程。返回的字符串已经是可直接填入 prompt 的多段落文本。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SimilarAuditExperienceService {

    private static final int DEFAULT_TOP_K = 3;
    private static final double DEFAULT_THRESHOLD = 0.75;

    private final SearchFeign searchFeign;

    public String retrieve(String content) {
        if (content == null || content.trim().isEmpty()) {
            return "";
        }
        try {
            AuditExperienceSearchReqDto req = new AuditExperienceSearchReqDto();
            req.setContentText(content);
            req.setTopK(DEFAULT_TOP_K);
            req.setSimilarityThreshold(DEFAULT_THRESHOLD);

            RestResp<List<AuditExperienceSearchRespDto>> resp = searchFeign.searchAuditExperience(req);
            if (resp == null || !resp.isOk() || resp.getData() == null || resp.getData().isEmpty()) {
                return "";
            }
            return format(resp.getData());
        } catch (Exception e) {
            log.error("获取相似审核经验失败", e);
            return "";
        }
    }

    private String format(List<AuditExperienceSearchRespDto> experiences) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < experiences.size(); i++) {
            AuditExperienceSearchRespDto exp = experiences.get(i);
            sb.append(String.format("判例%d：%n", i + 1));
            sb.append(String.format("- 核心争议片段：%s%n", nullToNone(exp.getKeySnippet())));
            sb.append(String.format("- 违规标签：%s%n", nullToNone(exp.getViolationLabel())));
            sb.append(String.format("- 判例规则总结：%s%n", nullToNone(exp.getAuditRule())));
            sb.append(String.format("- 历史审核结果：%s%n",
                    exp.getAuditStatus() != null && exp.getAuditStatus() == 1 ? "通过" : "不通过"));
            sb.append("\n");
        }
        return sb.toString();
    }

    private static String nullToNone(String value) {
        return value != null ? value : "无";
    }
}
