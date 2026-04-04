package com.novel.search.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import com.novel.book.dto.resp.ContentAuditRespDto;
import com.novel.book.feign.BookFeign;
import com.novel.common.resp.RestResp;
import com.novel.search.service.AuditExperienceSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditExperienceSyncServiceImpl implements AuditExperienceSyncService {

    private final BookFeign bookFeign;
    private final EmbeddingModel embeddingModel;
    private final ElasticsearchClient elasticsearchClient;

    private static final String INDEX_NAME = "audit_experience_index";

    @Override
    public void syncAllAuditExperienceToEs() {
        log.info("开始全量同步审核经验到 ES 向量库...");
        
        Long maxId = 0L;
        int totalCount = 0;
        
        while (true) {
            try {
                RestResp<List<ContentAuditRespDto>> resp = bookFeign.listNextAuditExperience(maxId);
                if (!resp.isOk() || resp.getData() == null || resp.getData().isEmpty()) {
                    break;
                }
                
                List<ContentAuditRespDto> auditList = resp.getData();
                processBatch(auditList);
                
                totalCount += auditList.size();
                maxId = auditList.get(auditList.size() - 1).getId();
                
                log.info("已同步 {} 条记录，当前 maxId: {}", totalCount, maxId);
            } catch (Exception e) {
                log.error("同步批次数据到 ES 失败, maxId: {}", maxId, e);
                break;
            }
        }
        
        log.info("全量同步审核经验到 ES 向量库完成！共处理 {} 条记录", totalCount);
    }

    private void processBatch(List<ContentAuditRespDto> auditList) throws Exception {
        BulkRequest.Builder br = new BulkRequest.Builder();

        for (ContentAuditRespDto audit : auditList) {
            // 过滤掉尚未提炼标签的数据（保证只同步高质量经验）
            if (audit.getViolationLabel() == null || audit.getViolationLabel().trim().isEmpty()) {
                continue;
            }

            // 组合提炼后的核心字段作为向量化内容
            String textToEmbed = "标签:" + audit.getViolationLabel() + 
                               " 规则:" + audit.getAuditRule() + 
                               " 片段:" + audit.getKeySnippet();

            // 调用大模型生成文本的向量 (Embedding)
            float[] embeddingArray = embeddingModel.embed(textToEmbed);
            List<Float> vector = new java.util.ArrayList<>();
            for (float v : embeddingArray) {
                vector.add(v);
            }
            
            // 添加延迟，避免触发大模型 API 的流控限制 (HTTP 429)
            try {
                Thread.sleep(500); // 延迟 500ms
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }

            // 构建存入 ES 的文档结构（极简模式，专为 RAG 优化）
            Map<String, Object> doc = new HashMap<>();
            doc.put("id", audit.getId().toString()); // 保留ID用于幂等更新和溯源
            doc.put("audit_status", audit.getAuditStatus()); // 必须保留：告诉大模型这个案例是“通过”还是“不通过”
            doc.put("violation_label", audit.getViolationLabel()); // 必须保留：分类标签
            doc.put("key_snippet", audit.getKeySnippet()); // 必须保留：核心争议片段
            doc.put("audit_rule", audit.getAuditRule()); // 必须保留：判例规则
            doc.put("content_vector", vector); // 必须保留：用于KNN检索

            // 添加到批量请求中
            br.operations(op -> op
                .index(idx -> idx
                    .index(INDEX_NAME)
                    .id(audit.getId().toString())
                    .document(doc)
                )
            );
        }

        // 执行批量写入
        BulkRequest request = br.build();
        if (!request.operations().isEmpty()) {
            BulkResponse result = elasticsearchClient.bulk(request);
            if (result.errors()) {
                log.error("批量写入 ES 存在错误: {}", result.items().stream()
                        .filter(item -> item.error() != null)
                        .map(item -> item.error().reason())
                        .findFirst().orElse("Unknown error"));
            } else {
                log.info("成功批量写入 {} 条记录到 ES", auditList.size());
            }
        }
    }
}
