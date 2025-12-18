package com.novel.book.service;

import com.novel.book.dao.entity.BookChapter;
import com.novel.book.dao.entity.BookInfo;
import com.novel.common.resp.RestResp;

/**
 * 书籍审核服务接口
 */
public interface BookAuditService {

    /**
     * AI审核书籍基本信息（小说名和描述）
     * @param bookInfo 书籍信息
     */
    void auditBookInfo(BookInfo bookInfo);

    /**
     * AI审核章节内容
     * @param bookChapter 章节信息
     */
    void auditChapter(BookChapter bookChapter);

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