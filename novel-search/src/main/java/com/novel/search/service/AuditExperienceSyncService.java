package com.novel.search.service;

public interface AuditExperienceSyncService {
    /**
     * 全量同步已审核的数据到 ES 向量库
     */
    void syncAllAuditExperienceToEs();
}
