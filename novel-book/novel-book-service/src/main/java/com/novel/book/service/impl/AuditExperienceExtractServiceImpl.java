package com.novel.book.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.novel.ai.dto.req.AuditRuleReqDto;
import com.novel.ai.dto.resp.AuditRuleRespDto;
import com.novel.ai.feign.AiFeign;
import com.novel.book.dao.entity.ContentAudit;
import com.novel.book.dao.mapper.ContentAuditMapper;
import com.novel.book.service.AuditExperienceExtractService;
import com.novel.common.resp.RestResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditExperienceExtractServiceImpl implements AuditExperienceExtractService {

    private final ContentAuditMapper contentAuditMapper;
    private final AiFeign aiFeign;

    private static final int BATCH_SIZE = 50;

    @Override
    public void extractAllMissingAuditExperience() {
        log.info("开始执行历史审核经验提炼任务...");

        // 查询需要提炼的数据总数：人工最终裁决，且还没提炼过标签的数据
        QueryWrapper<ContentAudit> countWrapper = new QueryWrapper<>();
        countWrapper.in("audit_status", 1, 2)
                .eq("is_human_final", 1)
                .isNotNull("content_text")
                .ne("content_text", "")
                .and(wrapper -> wrapper.isNull("violation_label").or().eq("violation_label", ""));

        long total = contentAuditMapper.selectCount(countWrapper);
        log.info("共找到 {} 条需要提炼的审核记录", total);

        int successCount = 0;
        long maxId = 0L;

        while (true) {
            QueryWrapper<ContentAudit> queryWrapper = new QueryWrapper<>();
            queryWrapper.in("audit_status", 1, 2)
                    .eq("is_human_final", 1)
                    .isNotNull("content_text")
                    .ne("content_text", "")
                    .and(wrapper -> wrapper.isNull("violation_label").or().eq("violation_label", ""))
                    .gt("id", maxId)
                    .orderByAsc("id")
                    .last("LIMIT " + BATCH_SIZE);

            List<ContentAudit> auditList = contentAuditMapper.selectList(queryWrapper);

            if (auditList.isEmpty()) {
                break;
            }

            for (ContentAudit audit : auditList) {
                maxId = audit.getId(); // 记录当前批次处理到的最大ID
                try {
                    log.info("开始提炼审核规则，auditId: {}", audit.getId());
                    AuditRuleReqDto ruleReq = new AuditRuleReqDto();
                    ruleReq.setContentText(audit.getContentText());
                    ruleReq.setAuditStatus(audit.getAuditStatus());
                    ruleReq.setAuditReason(audit.getAuditReason());

                    RestResp<AuditRuleRespDto> ruleResp = aiFeign.extractAuditRule(ruleReq);
                    if (ruleResp.isOk() && ruleResp.getData() != null) {
                        AuditRuleRespDto ruleData = ruleResp.getData();

                        // 更新回数据库
                        ContentAudit updateRuleAudit = new ContentAudit();
                        updateRuleAudit.setViolationLabel(ruleData.getViolationLabel());
                        updateRuleAudit.setKeySnippet(ruleData.getKeySnippet());
                        updateRuleAudit.setAuditRule(ruleData.getAuditRule());

                        QueryWrapper<ContentAudit> ruleUpdateWrapper = new QueryWrapper<>();
                        ruleUpdateWrapper.eq("id", audit.getId());
                        contentAuditMapper.update(updateRuleAudit, ruleUpdateWrapper);

                        successCount++;
                        log.info("提炼审核规则成功，auditId: {}, label: {}", audit.getId(), ruleData.getViolationLabel());
                    } else {
                        log.warn("提炼审核规则失败，auditId: {}, 响应: {}", audit.getId(), ruleResp.getMessage());
                    }

                    // 添加延迟，避免触发大模型 API 的流控限制 (HTTP 429)
                    Thread.sleep(1000);

                } catch (Exception e) {
                    log.error("提炼审核规则异常，auditId: {}", audit.getId(), e);
                }
            }
        }

        log.info("历史审核经验提炼任务执行完成！共成功处理 {} 条记录", successCount);
    }
}
