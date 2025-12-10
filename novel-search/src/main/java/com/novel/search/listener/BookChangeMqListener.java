package com.novel.search.listener;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.novel.book.dto.resp.BookEsRespDto;
import com.novel.book.manager.feign.BookFeign;
import com.novel.common.constant.AmqpConsts;
import com.novel.common.resp.RestResp;
import com.novel.search.constant.EsConsts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

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
public class BookChangeMqListener implements RocketMQListener<Long> {

    private final ElasticsearchClient elasticsearchClient;
    private final BookFeign bookFeign;

    @Override
    public void onMessage(Long bookId) {
        log.info(">>> [MQ] 收到书籍变更消息，bookId={}", bookId);
        
        if (bookId == null) {
            return;
        }

        try {
            // 1. 调用 Feign 获取最新书籍数据
            // 这里调用的是我们刚刚添加的接口
            RestResp<BookEsRespDto> resp = bookFeign.getEsBookById(bookId);
            
            if (!resp.isOk()) {
                log.error(">>> [MQ] 调用 Feign 获取书籍数据失败，bookId={}，Code={}，Msg={}", 
                          bookId, resp.getCode(), resp.getMessage());
                // 如果是服务调用失败，抛出异常让 MQ 重试
                throw new RuntimeException("Feign调用失败: " + resp.getMessage());
            }

            BookEsRespDto book = resp.getData();
            if (book == null) {
                // 如果查不到数据，可能是书籍被删除了？
                // 根据业务逻辑，这里可能需要从 ES 删除，或者直接忽略
                log.warn(">>> [MQ] 查无此书，bookId={}", bookId);
                // 尝试从 ES 删除（防止是删除操作触发的更新）
                deleteFromEs(bookId);
                return;
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
            throw new RuntimeException("ES同步失败", e);
        }
    }
    
    /**
     * 从 ES 删除数据（可选辅助方法）
     */
    private void deleteFromEs(Long bookId) {
        try {
            elasticsearchClient.delete(d -> d
                .index(EsConsts.BookIndex.INDEX_NAME)
                .id(bookId.toString())
            );
            log.info(">>> [MQ] 从 ES 删除书籍成功。bookId={}", bookId);
        } catch (IOException e) {
            log.error(">>> [MQ] 从 ES 删除书籍失败。bookId={}", bookId, e);
            // 【优化】抛出异常，让 MQ 重试，确保数据一致性
            throw new RuntimeException("ES删除失败", e);
        }
    }
}
