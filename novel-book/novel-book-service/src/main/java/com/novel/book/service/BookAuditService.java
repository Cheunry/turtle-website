package com.novel.book.service;

import com.novel.book.dao.entity.BookInfo;
import com.novel.book.dto.mq.BookAuditResultMqDto;
import com.novel.book.dto.mq.ChapterAuditResultMqDto;
import com.novel.common.resp.RestResp;

/**
 * 书籍审核服务接口
 */
public interface BookAuditService {

    /**
     * 处理章节审核结果（MQ异步回调方式）
     * @param resultDto 审核结果DTO
     */
    void processChapterAuditResult(ChapterAuditResultMqDto resultDto);

    /**
     * 处理书籍审核结果（MQ异步回调方式）
     * @param resultDto 审核结果DTO
     */
    void processBookAuditResult(BookAuditResultMqDto resultDto);

    /**
     * 人工审核
     * @param auditId 审核记录ID
     * @param auditStatus 审核状态（1-通过 2-不通过）
     * @param auditReason 审核原因
     * @return 响应
     */
    RestResp<Void> manualAudit(Long auditId, Integer auditStatus, String auditReason);

    /**
     * 提取简短审核原因（用于展示给作者）
     * @param fullReason AI返回的完整原因
     * @param aiConfidence AI置信度
     * @return 简短原因
     */
    String extractShortReason(String fullReason, java.math.BigDecimal aiConfidence);


}