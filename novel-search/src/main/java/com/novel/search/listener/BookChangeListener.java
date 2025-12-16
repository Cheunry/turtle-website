package com.novel.search.listener;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.novel.book.dto.resp.BookEsRespDto;
import com.novel.common.constant.AmqpConsts;
import com.novel.common.constant.EsConsts;
import com.novel.search.feign.BookFeignManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 小说信息变动 监听器
 */
@Component
@Slf4j
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = AmqpConsts.BookChangeMq.TOPIC,
        consumerGroup = AmqpConsts.BookChangeMq.CONSUMER_GROUP_ES,
        selectorExpression = AmqpConsts.BookChangeMq.TAG_UPDATE
)
public class BookChangeListener implements RocketMQListener<Long> {

    private final BookFeignManager bookFeignManager;
    private final ElasticsearchClient elasticsearchClient;

    @Override
    public void onMessage(Long bookId) {
        log.info(">>> 收到小说信息变动消息，bookId: {}", bookId);
        // 1. 查询最新小说信息
        BookEsRespDto book = bookFeignManager.getEsBookById(bookId);
        if (book == null) {
            log.warn(">>> 未查询到小说信息，可能已被删除，bookId: {}", bookId);
            // 这里也可以选择执行删除 ES 索引的操作
            return;
        }

        // 2. 更新 ES
        try {
            elasticsearchClient.index(i -> i
                    .index(EsConsts.BookIndex.INDEX_NAME)
                    .id(book.getId().toString())
                    .document(book)
            );
            log.info(">>> ES 更新成功，bookId: {}", bookId);
        } catch (IOException e) {
            log.error(">>> ES 更新失败，bookId: {}", bookId, e);
        }
    }
}
