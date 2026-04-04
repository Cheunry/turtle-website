package com.novel.search.config;

import com.novel.search.service.AuditExperienceSyncService;
import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuditExperienceToEsTask {

    private final AuditExperienceSyncService auditExperienceSyncService;

    /**
     * 全量审核经验数据同步
     * 可以通过 XXL-Job 手动触发或定时执行
     */
    @XxlJob("syncAuditExperienceToEsJobHandler")
    public ReturnT<String> syncAuditExperienceToEs() {
        log.info(">>> ========== 开始执行审核经验全量同步任务 ==========");
        try {
            auditExperienceSyncService.syncAllAuditExperienceToEs();
            log.info(">>> ========== 审核经验全量同步任务执行完成 ==========");
            return ReturnT.SUCCESS;
        } catch (Exception e) {
            log.error(">>> ========== 审核经验全量同步任务执行失败 ==========", e);
            return ReturnT.FAIL;
        }
    }
}
