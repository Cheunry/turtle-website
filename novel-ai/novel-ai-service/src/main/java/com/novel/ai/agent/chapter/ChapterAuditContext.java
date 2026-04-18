package com.novel.ai.agent.chapter;

import com.novel.ai.agent.core.AuditContext;
import com.novel.book.dto.req.ChapterAuditReqDto;
import com.novel.book.dto.resp.ChapterAuditRespDto;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 章节审核流水线上下文。章节内容可能较长并被切分为多段，因此上下文里既保留
 * 切分后的 {@link #segments}，也保留每段独立的审核响应 {@link #segmentResults}，
 * 便于后续合并步骤使用。
 */
@Getter
@Setter
public class ChapterAuditContext extends AuditContext<ChapterAuditReqDto, ChapterAuditRespDto> {

    /** 切分后的段文本，未分段时只含一项。 */
    private List<String> segments = Collections.emptyList();

    /** 各段的审核响应；在 {@link com.novel.ai.agent.chapter.step.ChapterSegmentAuditStep} 中逐项填充。 */
    private final List<ChapterAuditRespDto> segmentResults = new ArrayList<>();

    public ChapterAuditContext(ChapterAuditReqDto request) {
        super(request, "audit_chapter");
    }
}
