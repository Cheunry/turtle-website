package com.novel.search.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Time;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import com.novel.book.dto.resp.BookEsRespDto;
import com.novel.common.constant.EsConsts;
import com.novel.search.feign.BookFeignManager;
import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class AllBookToEsTask {

    private final BookFeignManager bookFeignManager;
    private final ElasticsearchClient elasticsearchClient;
    private final EmbeddingModel embeddingModel;


    /**
     * 全量数据同步
     * 每天凌晨2点执行
     */
//    @SneakyThrows
//    @XxlJob("saveToEsJobHandler")
//    @Scheduled(cron = "0 0 2 * * ?")
    public ReturnT<String> saveToEs() {

        log.info(">>> 开始执行全量同步任务");
        try {
            long maxId = 0;
            for (; ; ) {
                List<BookEsRespDto> books = bookFeignManager.listEsBooks(maxId);
                if (books.isEmpty()) {
                    log.info(">>> 本次查询结果为空，同步结束。maxId={}", maxId);
                    break;
                }

                log.info(">>> 查询到 {} 条书籍数据，准备写入 ES，当前 maxId={}", books.size(), maxId);
                BulkRequest.Builder br = new BulkRequest.Builder();

                for (BookEsRespDto book : books) {

                    // --- 批量同步时的向量生成逻辑 ---
                    try {
                        String textToEmbed = "书名:" + book.getBookName() + 
                                           "; 作者:" + book.getAuthorName() + 
                                           "; 简介:" + book.getBookDesc();
                        if (textToEmbed.length() > 2000) {
                            textToEmbed = textToEmbed.substring(0, 2000);
                        }
                        
                        // 生成向量
                        float[] embeddingArray = embeddingModel.embed(textToEmbed);
                        log.info(">>> Generated embedding for bookId={}, length={}", book.getId(), embeddingArray.length);

                        List<Float> embeddingList = new ArrayList<>();
                        for (float v : embeddingArray) {
                            embeddingList.add(v);
                        }
                        book.setEmbedding(embeddingList);
                        
                    } catch (Exception e) {
                        log.error(">>> 全量同步-向量生成失败，bookId={}", book.getId(), e);
                    }
                    // ------------------------------------

                    br.operations(op -> op
                            .index(idx -> idx
                                    .index(EsConsts.BookIndex.INDEX_NAME)
                                    .id(book.getId().toString())
                                    .document(book)
                            )
                    ).timeout(Time.of(t -> t.time("10s")));

                    maxId = book.getId();
                }

                BulkResponse result = elasticsearchClient.bulk(br.build());

                // 打印写入结果
                log.info(">>> ES 写入完成，耗时: {}ms, 是否有错误: {}", result.took(), result.errors());

                // Log errors
                if (result.errors()) {
                    log.error("Bulk had errors");
                    for (BulkResponseItem item : result.items()) {
                        if (item.error() != null) {
                            log.error("Id: {}, Error Type: {}, Reason: {}", 
                                item.id(), 
                                item.error().type(), 
                                item.error().reason());
                        }
                    }
                }
            }
            return ReturnT.SUCCESS;

        } catch (Exception e) {

            log.error(e.getMessage(), e);
            return ReturnT.FAIL;
        }
    }

}
