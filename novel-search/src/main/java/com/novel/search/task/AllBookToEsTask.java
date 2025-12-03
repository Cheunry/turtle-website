package com.novel.search.task;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Time;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import com.novel.book.dto.resp.BookEsRespDto;
import com.novel.search.constant.EsConsts;
import com.novel.search.manager.feign.BookFeignManager;
import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class AllBookToEsTask {

    private final BookFeignManager bookFeignManager;
    private final ElasticsearchClient elasticsearchClient;


    /**
     * 全量数据同步
     */
    @SneakyThrows
    @XxlJob("saveToEsJobHandler")
    public ReturnT<String> saveToEs() {
        // {{ edit_1 }} 添加开始日志
        log.info(">>> 开始执行全量同步任务");
        // {{ edit_1 }}
        try {
            long maxId = 0;
            for (; ; ) {
                List<BookEsRespDto> books = bookFeignManager.listEsBooks(maxId);
                // {{ edit_2 }} 打印查到的数据条数
                if (books.isEmpty()) {
                    log.info(">>> 本次查询结果为空，同步结束。maxId={}", maxId);
                    break;
                }
                log.info(">>> 查询到 {} 条书籍数据，准备写入 ES，当前 maxId={}", books.size(), maxId);
                // {{ edit_2 }}
                BulkRequest.Builder br = new BulkRequest.Builder();

                for (BookEsRespDto book : books) {
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
                
                // {{ edit_3 }} 打印写入结果
                log.info(">>> ES 写入完成，耗时: {}ms, 是否有错误: {}", result.took(), result.errors());
                // {{ edit_3 }}

                // Log errors, if any
                if (result.errors()) {
                    log.error("Bulk had errors");
                    for (BulkResponseItem item : result.items()) {
                        if (item.error() != null) {
                            log.error(item.error().reason());
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
