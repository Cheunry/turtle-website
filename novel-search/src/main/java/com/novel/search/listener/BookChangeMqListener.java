package com.novel.search.listener;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import com.novel.book.dto.resp.BookEsRespDto;
import com.novel.search.feign.BookFeignManager;
import com.novel.common.constant.AmqpConsts;
import com.novel.common.constant.EsConsts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;

/**
 * 书籍变更 MQ 监听器
 * 用于实时同步 ES 数据
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
    topic = AmqpConsts.BookChangeMq.TOPIC, 
    consumerGroup = AmqpConsts.BookChangeMq.CONSUMER_GROUP_ES
)
public class BookChangeMqListener implements RocketMQListener<String> {

    private final ElasticsearchClient elasticsearchClient;
    private final BookFeignManager bookFeignManager;
    private final ObjectMapper objectMapper;
    private final EmbeddingModel embeddingModel;

    @Override
    public void onMessage(String message) {
        if (message == null || message.isEmpty()) {
            return;
        }
        log.info(">>> [MQ] 收到书籍变更消息，message={}", message);

        List<Long> bookIds = new ArrayList<>();
        try {
            // 尝试解析为 List<Long>
            if (message.startsWith("[")) {
                bookIds = objectMapper.readValue(message, new TypeReference<List<Long>>() {});
            } else {
                // 尝试解析为单个 Long
                bookIds.add(Long.valueOf(message));
            }
        } catch (JsonProcessingException | NumberFormatException e) {
            log.error(">>> [MQ] 消息格式错误，无法解析: {}", message, e);
            // 格式错误不需要重试，直接返回
            return;
        }

        if (bookIds.isEmpty()) {
            return;
        }

        for (Long bookId : bookIds) {
            try {
                // 1. 通过 BookFeignManager 安全地获取最新书籍数据
                BookEsRespDto book = bookFeignManager.getEsBookById(bookId);
                
                if (book == null) {
                    // 如果查不到数据，可能是：
                    // 1. 书籍被删除了
                    // 2. 书籍未审核通过（auditStatus != 1）
                    // 3. Feign 调用失败（BookFeignManager 已记录警告日志）
                    // 4. 新增书籍但服务调用失败（ES 中本来就没有数据）
                    // 
                    // 重要：只有当 ES 中存在该书籍时，才需要删除
                    // 如果是新增书籍，ES 中本来就没有，不需要删除
                    if (existsInEs(bookId)) {
                        log.warn(">>> [MQ] 书籍数据为空，但 ES 中存在该书籍，可能原因：1)书籍被删除 2)书籍未审核通过，bookId={}，将从ES删除", bookId);
                        deleteFromEs(bookId);
                    } else {
                        log.warn(">>> [MQ] 书籍数据为空，且 ES 中不存在该书籍，可能原因：1)新增书籍但服务调用失败 2)消息错误，bookId={}，跳过处理", bookId);
                    }
                    continue;
                }

                // --- 生成向量 ---
                try {
                    // 组合书名、作者和简介作为向量化内容
                    String textToEmbed = "书名:" + book.getBookName() + 
                                       "; 作者:" + book.getAuthorName() + 
                                       "; 简介:" + book.getBookDesc();
                    
                    // 简单截断文本以防过长 (假设模型限制为 8k token，这里保守截取 2000 字符)
                    if (textToEmbed.length() > 2000) {
                        textToEmbed = textToEmbed.substring(0, 2000);
                    }
                    
                    float[] embeddingArray = embeddingModel.embed(textToEmbed);
                    List<Float> embeddingList = new ArrayList<>();
                    for (float v : embeddingArray) {
                        embeddingList.add(v);
                    }
                    book.setEmbedding(embeddingList);
                    log.debug(">>> [MQ] 已为书籍生成向量。bookId={}", bookId);
                } catch (Exception e) {
                    log.error(">>> [MQ] 向量生成失败，将只保存文本字段。bookId={}", bookId, e);
                }


                // 2. 更新 ES
                elasticsearchClient.index(idx -> idx
                        .index(EsConsts.BookIndex.INDEX_NAME)
                        .id(book.getId().toString())
                        .document(book)
                );
                
                log.info(">>> [MQ] ES 索引更新成功。bookId={}", bookId);

            } catch (Exception e) {
                log.error(">>> [MQ] ES 索引更新失败。bookId={}", bookId, e);
                // 抛出异常，RocketMQ 会根据重试策略进行重试
                // 注意：在批量消费中，抛出异常会导致整批消息重试，这可能是个副作用
                throw new RuntimeException("ES同步失败", e);
            }
        }
    }
    
    /**
     * 检查 ES 中是否存在该书籍
     */
    private boolean existsInEs(Long bookId) {
        try {
            return elasticsearchClient.exists(e -> e
                .index(EsConsts.BookIndex.INDEX_NAME)
                .id(bookId.toString())
            ).value();
        } catch (Exception e) {
            log.warn(">>> [MQ] 检查 ES 中是否存在书籍时发生异常。bookId={}", bookId, e);
            // 如果检查失败，保守处理：假设存在，让删除逻辑处理
            return true;
        }
    }
    
    /**
     * 从 ES 删除数据（辅助方法）
     * 如果文档不存在（404），则忽略，不抛出异常
     */
    private void deleteFromEs(Long bookId) {
        try {
            elasticsearchClient.delete(d -> d
                .index(EsConsts.BookIndex.INDEX_NAME)
                .id(bookId.toString())
            );
            log.info(">>> [MQ] 从 ES 删除书籍成功。bookId={}", bookId);
        } catch (ElasticsearchException e) {
            // 如果是404错误（文档不存在），这是正常的，不需要重试
            if (e.status() == 404) {
                log.debug(">>> [MQ] ES 中不存在该书籍，无需删除。bookId={}", bookId);
                return;
            }
            // 其他ES错误，记录日志但不抛出异常，避免MQ重试
            log.warn(">>> [MQ] 从 ES 删除书籍失败（非404错误）。bookId={}, status={}", bookId, e.status(), e);
        } catch (IOException e) {
            // IO异常，记录日志但不抛出异常，避免MQ重试
            log.warn(">>> [MQ] 从 ES 删除书籍时发生IO异常。bookId={}", bookId, e);
        } catch (Exception e) {
            // 其他异常，记录日志但不抛出异常，避免MQ重试
            log.warn(">>> [MQ] 从 ES 删除书籍时发生未知异常。bookId={}", bookId, e);
        }
    }
}
