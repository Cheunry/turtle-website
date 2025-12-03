package com.novel.search.task;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.novel.book.dto.resp.BookEsRespDto;
import com.novel.common.constant.AmqpConsts;
import com.novel.search.constant.EsConsts;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 监听小说信息变更 MQ，更新 ES
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UptdateBookToEsTask {

    private final ElasticsearchClient esClient;

    /**
     * 监听小说信息变更队列
     * 假设 MQ 发送的消息体就是 BookEsRespDto
     */
    @SneakyThrows
    @RabbitListener(queues = AmqpConsts.BookChangeMq.QUEUE_ES_UPDATE)
    public void updateBookToEs(BookEsRespDto bookDto) {
        log.info("收到小说信息变更消息，准备更新 ES，BookID: {}", bookDto.getId());
        
        esClient.index(i -> i
                .index(EsConsts.BookIndex.INDEX_NAME)
                .id(bookDto.getId().toString())
                .document(bookDto)
        );
        
        log.info("ES 更新成功，BookID: {}", bookDto.getId());
    }
}
