package com.novel.search.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.TotalHits;
import co.elastic.clients.json.JsonData;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.novel.book.dto.req.BookSearchReqDto;
import com.novel.book.dto.resp.BookEsRespDto;
import com.novel.book.dto.resp.BookInfoRespDto;
import com.novel.common.resp.PageRespDto;
import com.novel.common.resp.RestResp;
import com.novel.common.constant.EsConsts;
import com.novel.search.service.SearchService;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SearchServiceImpl implements SearchService {

    private final ElasticsearchClient esClient;
    private final EmbeddingModel embeddingModel;

    @SneakyThrows
    @Override
    public RestResp<PageRespDto<BookInfoRespDto>> searchBooks(BookSearchReqDto condition) {


        // 1. 构建基础过滤条件 (BoolQuery)
        BoolQuery.Builder boolQueryBuilder = new BoolQuery.Builder();
        buildSearchCondition(condition, boolQueryBuilder);
        BoolQuery boolQuery = boolQueryBuilder.build();

        // 2. 核心逻辑：判断是 Hybrid Search 还是 普通 Search
        boolean useHybridSearch = false;
        if (!StringUtils.isBlank(condition.getKeyword())) {
            int mode = condition.getSearchMode() == null ? 0 : condition.getSearchMode();
            if (mode == 1) {
                // AI 混合搜索 (搜情节)
                useHybridSearch = true;
            } else if (mode == 0) {
                 // 智能自动模式
                 // 策略：如果关键词长度超过 6 个字，或者包含空格（可能是句子），则认为是搜情节
                 if (condition.getKeyword().length() > 6 || condition.getKeyword().contains(" ")) {
                     useHybridSearch = true;
                 }
            }
            // 模式为2 (或其他) 时，强制走文本搜索(false)
        }

        if (useHybridSearch) {
            try {
                // --- Hybrid Search 模式（手动合并，不使用 RRF）---
                // 分别执行 KNN 和 BM25 查询，然后在 Java 端合并结果

                // A. 生成查询向量
                float[] queryVectorArray = embeddingModel.embed(condition.getKeyword());
                List<Float> queryVector = new ArrayList<>();
                for (float v : queryVectorArray) {
                    queryVector.add(v);
                }

                // B. 执行 KNN 查询（向量检索）
                int recallSize = Math.max(condition.getPageSize() * 3, 100); // 召回更多结果用于合并
                SearchResponse<BookEsRespDto> knnResponse = esClient.search(s -> s
                    .index(EsConsts.BookIndex.INDEX_NAME)
                    .knn(knn -> knn
                        .field("embedding")
                        .k(recallSize)
                        .numCandidates(Math.max(recallSize * 2, 200))
                        .queryVector(queryVector)
                        .filter(f -> f.bool(boolQuery))
                    )
                    .size(recallSize)
                    .source(src -> src.filter(f -> f.includes("*")))
                , BookEsRespDto.class);

                // C. 执行 BM25 查询（文本检索）
                SearchResponse<BookEsRespDto> bm25Response = esClient.search(s -> {
                    SearchRequest.Builder builder = s.index(EsConsts.BookIndex.INDEX_NAME)
                        .query(q -> q.bool(boolQuery))
                        .size(recallSize)
                        .highlight(h -> h.fields(EsConsts.BookIndex.FIELD_BOOK_NAME,
                                        t -> t.preTags("<em style='color:red'>").postTags("</em>"))
                                .fields(EsConsts.BookIndex.FIELD_AUTHOR_NAME,
                                        t -> t.preTags("<em style='color:red'>").postTags("</em>")));
                    applySort(condition, builder);
                    return builder;
                }, BookEsRespDto.class);

                // D. 手动合并结果（使用简单的分数加权）
                Map<Long, CombinedResult> combinedMap = new HashMap<>();
                
                // 处理 KNN 结果（向量相似度分数）
                List<Hit<BookEsRespDto>> knnHits = knnResponse.hits().hits();
                for (int i = 0; i < knnHits.size(); i++) {
                    Hit<BookEsRespDto> hit = knnHits.get(i);
                    BookEsRespDto book = hit.source();
                    if (book == null) continue;
                    
                    // KNN 分数：使用排名倒数（RRF 的简化版）
                    double knnScore = 1.0 / (i + 1.0);
                    CombinedResult result = combinedMap.computeIfAbsent(book.getId(), 
                        k -> new CombinedResult(book));
                    result.knnScore = knnScore;
                }

                // 处理 BM25 结果（文本匹配分数）
                List<Hit<BookEsRespDto>> bm25Hits = bm25Response.hits().hits();
                for (int i = 0; i < bm25Hits.size(); i++) {
                    Hit<BookEsRespDto> hit = bm25Hits.get(i);
                    BookEsRespDto book = hit.source();
                    if (book == null) continue;
                    
                    // BM25 分数：使用排名倒数
                    double bm25Score = 1.0 / (i + 1.0);
                    CombinedResult result = combinedMap.computeIfAbsent(book.getId(), 
                        k -> new CombinedResult(book));
                    result.bm25Score = bm25Score;
                    
                    // 保存高亮信息（BM25 才有高亮）
                    if (hit.highlight() != null) {
                        if (!CollectionUtils.isEmpty(hit.highlight().get(EsConsts.BookIndex.FIELD_BOOK_NAME))) {
                            result.book.setBookName(hit.highlight().get(EsConsts.BookIndex.FIELD_BOOK_NAME).getFirst());
                        }
                        if (!CollectionUtils.isEmpty(hit.highlight().get(EsConsts.BookIndex.FIELD_AUTHOR_NAME))) {
                            result.book.setAuthorName(hit.highlight().get(EsConsts.BookIndex.FIELD_AUTHOR_NAME).getFirst());
                        }
                    }
                }

                // E. 按综合分数排序（权重：向量 0.6，文本 0.4，可根据效果调整）
                double vectorWeight = 0.6;
                double textWeight = 0.4;
                
                List<CombinedResult> sortedResults = combinedMap.values().stream()
                    .peek(r -> r.combinedScore = r.knnScore * vectorWeight + r.bm25Score * textWeight)
                    .sorted((a, b) -> Double.compare(b.combinedScore, a.combinedScore))
                    .toList();

                // F. 分页
                int from = (condition.getPageNum() - 1) * condition.getPageSize();
                int to = Math.min(from + condition.getPageSize(), sortedResults.size());
                List<CombinedResult> pagedResults = sortedResults.subList(from, to);

                // G. 转换为 DTO
                List<BookInfoRespDto> list = pagedResults.stream()
                    .map(r -> BookInfoRespDto.builder()
                        .id(r.book.getId())
                        .bookName(r.book.getBookName())
                        .categoryName(r.book.getCategoryName())
                        .authorName(r.book.getAuthorName())
                        .wordCount(r.book.getWordCount())
                        .lastChapterName(r.book.getLastChapterName())
                        .bookStatus(r.book.getBookStatus())
                        .picUrl(r.book.getPicUrl())
                        .build())
                    .collect(Collectors.toList());

                long totalValue = combinedMap.size();
                
                return RestResp.ok(
                    PageRespDto.of(condition.getPageNum(), condition.getPageSize(), totalValue, list));

            } catch (Exception e) {
                log.error("向量生成或混合检索失败，降级为普通文本搜索", e);
                // 降级：只使用文本搜索
                return executeTextOnlySearch(condition, boolQuery);
            }
        } else {
            // --- 普通筛选模式 ---
            return executeTextOnlySearch(condition, boolQuery);
        }
    }

    /**
     * 执行纯文本搜索（无向量）
     */
    @SneakyThrows
    private RestResp<PageRespDto<BookInfoRespDto>> executeTextOnlySearch(
            BookSearchReqDto condition, BoolQuery boolQuery) {
                
        SearchResponse<BookEsRespDto> response = esClient.search(s -> {
            SearchRequest.Builder builder = s.index(EsConsts.BookIndex.INDEX_NAME)
                .query(q -> q.bool(boolQuery))
                .from((condition.getPageNum() - 1) * condition.getPageSize())
                .size(condition.getPageSize())
                .highlight(h -> h.fields(EsConsts.BookIndex.FIELD_BOOK_NAME,
                                t -> t.preTags("<em style='color:red'>").postTags("</em>"))
                        .fields(EsConsts.BookIndex.FIELD_AUTHOR_NAME,
                                t -> t.preTags("<em style='color:red'>").postTags("</em>")));
            applySort(condition, builder);
            return builder;
        }, BookEsRespDto.class);

        TotalHits total = response.hits().total();
        List<BookInfoRespDto> list = new ArrayList<>();
        
        for (Hit<BookEsRespDto> hit : response.hits().hits()) {
            BookEsRespDto book = hit.source();
            if (book == null) continue;

            // 处理高亮
            if (hit.highlight() != null) {
                if (!CollectionUtils.isEmpty(hit.highlight().get(EsConsts.BookIndex.FIELD_BOOK_NAME))) {
                    book.setBookName(hit.highlight().get(EsConsts.BookIndex.FIELD_BOOK_NAME).getFirst());
                }
                if (!CollectionUtils.isEmpty(hit.highlight().get(EsConsts.BookIndex.FIELD_AUTHOR_NAME))) {
                    book.setAuthorName(hit.highlight().get(EsConsts.BookIndex.FIELD_AUTHOR_NAME).getFirst());
                }
            }
            
            list.add(BookInfoRespDto.builder()
                    .id(book.getId())
                    .bookName(book.getBookName())
                    .categoryName(book.getCategoryName())
                    .authorName(book.getAuthorName())
                    .wordCount(book.getWordCount())
                    .lastChapterName(book.getLastChapterName())
                    .bookStatus(book.getBookStatus())
                    .picUrl(book.getPicUrl())
                    .build());
        }

        long totalValue = total != null ? total.value() : 0;
        
        return RestResp.ok(
                PageRespDto.of(condition.getPageNum(), condition.getPageSize(), totalValue, list));
    }

    /**
     * 内部类：用于合并 KNN 和 BM25 的结果
     */
    private static class CombinedResult {
        BookEsRespDto book;
        double knnScore = 0.0;
        double bm25Score = 0.0;
        double combinedScore = 0.0;

        CombinedResult(BookEsRespDto book) {
            this.book = book;
        }
    }

    /**
     * 应用排序规则
     */
    private void applySort(BookSearchReqDto condition, SearchRequest.Builder searchBuilder) {
        if (!StringUtils.isBlank(condition.getSort())) {
            searchBuilder.sort(o -> o.field(f -> f
                    .field(StringUtils.underlineToCamel(condition.getSort().split(" ")[0]))
                    .order(SortOrder.Desc))
            );
        }
    }

    /**
     * 构建检索条件 (BoolQuery)
     * 将之前的 void 方法改为填充 BoolQuery.Builder
     */
    private void buildSearchCondition(BookSearchReqDto condition,
                                      BoolQuery.Builder b) {

        // 只查有字数的小说
        b.must(RangeQuery.of(m -> m
                .field(EsConsts.BookIndex.FIELD_WORD_COUNT)
                .gt(JsonData.of(0))
        )._toQuery());

        if (!StringUtils.isBlank(condition.getKeyword())) {
            // 关键词匹配 (Text Search)
            // 根据 searchMode 动态调整匹配策略
            int mode = condition.getSearchMode() == null ? 0 : condition.getSearchMode();
            if (mode == 2) {
                // 模式2：只搜书名，精准+模糊组合
                // 解决特殊分词（如中英混合）导致match查询不全的问题
                b.must(m -> m.bool(bool -> bool
                    // 策略 A: 分词精确匹配 (要求分词全中)
                    .should(s -> s.match(t -> t
                        .field(EsConsts.BookIndex.FIELD_BOOK_NAME)
                        .query(condition.getKeyword())
                        .operator(co.elastic.clients.elasticsearch._types.query_dsl.Operator.And)
                    ))
                    // 策略 B: Keyword 通配符匹配 (解决分词搞不定的情况，类似 SQL 的 like %keyword%)
                    .should(s -> s.wildcard(w -> w
                        .field(EsConsts.BookIndex.FIELD_BOOK_NAME + ".keyword")
                        .value("*" + condition.getKeyword() + "*")
                    ))
                    // 只要满足 A 或 B 其中一个即可
                    .minimumShouldMatch("1") 
                ));
            } else {
                // 其他模式（智能/AI）：多字段匹配
                b.must((q -> q.multiMatch(t -> t
                        .fields(EsConsts.BookIndex.FIELD_BOOK_NAME + "^2",
                                EsConsts.BookIndex.FIELD_AUTHOR_NAME + "^1.8",
                                EsConsts.BookIndex.FIELD_BOOK_DESC + "^0.1")
                        .query(condition.getKeyword())
                )));
            }
        }

        // 精确查询
        if (Objects.nonNull(condition.getWorkDirection())) {
            b.must(TermQuery.of(m -> m
                    .field(EsConsts.BookIndex.FIELD_WORK_DIRECTION)
                    .value(condition.getWorkDirection())
            )._toQuery());
        }

        if (Objects.nonNull(condition.getCategoryId())) {
            b.must(TermQuery.of(m -> m
                    .field(EsConsts.BookIndex.FIELD_CATEGORY_ID)
                    .value(condition.getCategoryId())
            )._toQuery());
        }

        // 状态查询 (连载中/已完结)
        if (Objects.nonNull(condition.getBookStatus())) {
            b.must(TermQuery.of(m -> m
                    .field(EsConsts.BookIndex.FIELD_BOOK_STATUS)
                    .value(condition.getBookStatus())
            )._toQuery());
        }

        // 范围查询
        if (Objects.nonNull(condition.getWordCountMin())) {
            b.must(RangeQuery.of(m -> m
                    .field(EsConsts.BookIndex.FIELD_WORD_COUNT)
                    .gte(JsonData.of(condition.getWordCountMin()))
            )._toQuery());
        }

        if (Objects.nonNull(condition.getWordCountMax())) {
            b.must(RangeQuery.of(m -> m
                    .field(EsConsts.BookIndex.FIELD_WORD_COUNT)
                    .lt(JsonData.of(condition.getWordCountMax()))
            )._toQuery());
        }

        if (Objects.nonNull(condition.getUpdateTimeMin())) {
            b.must(RangeQuery.of(m -> m
                    .field(EsConsts.BookIndex.FIELD_LAST_CHAPTER_UPDATE_TIME)
                    .gte(JsonData.of(condition.getUpdateTimeMin().getTime()))
            )._toQuery());
        }
    }
}
