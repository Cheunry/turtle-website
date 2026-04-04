package com.novel.book.service;

public interface AuditExperienceExtractService {
    /**
     * 全量提取审核经验（补充历史数据中缺失的标签、片段和规则）
     */
    void extractAllMissingAuditExperience();
}
