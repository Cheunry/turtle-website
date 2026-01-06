# 搜索质量评估指南

本文档提供了一套完整的搜索质量评估体系，帮助你量化评估 AI 混合搜索的效果。

## 1. 核心评估指标

### 1.1 技术指标（可观测）

#### A. 响应时间（Latency）
- **指标名称**: `search_latency_ms`
- **定义**: 从用户发起搜索请求到返回结果的耗时（毫秒）
- **目标值**: 
  - P50 < 200ms（50% 的请求）
  - P95 < 500ms（95% 的请求）
  - P99 < 1000ms（99% 的请求）
- **观测方式**: 在 `SearchServiceImpl` 中记录时间戳

#### B. 向量生成耗时（Vector Generation Time）
- **指标名称**: `vector_gen_time_ms`
- **定义**: 调用 DashScope API 生成查询向量的耗时
- **目标值**: < 300ms（取决于网络和模型）
- **观测方式**: 在调用 `embeddingModel.embed()` 前后记录时间

#### C. 召回数量（Recall）
- **指标名称**: `search_recall_count`
- **定义**: 混合检索返回的总结果数（KNN + BM25 去重后）
- **观测方式**: 记录 `combinedMap.size()`

#### D. 结果分布（Result Distribution）
- **指标名称**: `knn_only_count`, `bm25_only_count`, `both_count`
- **定义**: 
  - `knn_only_count`: 只在向量检索中出现的结果数
  - `bm25_only_count`: 只在文本检索中出现的结果数
  - `both_count`: 两路检索都命中的结果数
- **意义**: 评估两路检索的互补性

### 1.2 业务指标（需要用户行为数据）

#### A. 点击率（CTR - Click Through Rate）
- **指标名称**: `search_ctr`
- **定义**: `点击搜索结果的数量 / 搜索请求总数`
- **目标值**: > 30%（行业平均水平）
- **实现**: 前端埋点，记录用户点击了哪个搜索结果

#### B. 平均点击位置（Average Click Position）
- **指标名称**: `avg_click_position`
- **定义**: 用户点击的结果在列表中的平均位置（1-based）
- **目标值**: < 3（说明前 3 个结果质量高）
- **实现**: 前端埋点，记录点击的索引

#### C. 搜索到阅读转化率（Search-to-Read Conversion）
- **指标名称**: `search_to_read_rate`
- **定义**: `从搜索结果进入阅读页面的用户数 / 搜索用户总数`
- **目标值**: > 15%
- **实现**: 追踪搜索 → 点击 → 阅读的完整链路

#### D. 无结果率（No Result Rate）
- **指标名称**: `no_result_rate`
- **定义**: `返回 0 结果的搜索请求数 / 总搜索请求数`
- **目标值**: < 5%
- **观测方式**: 记录 `totalValue == 0` 的请求

### 1.3 相关性指标（需要人工标注或用户反馈）

#### A. 精确率（Precision@K）
- **指标名称**: `precision_at_k`
- **定义**: 前 K 个结果中，相关结果的比例
- **计算**: 需要人工标注“相关/不相关”
- **目标值**: Precision@10 > 0.7

#### B. NDCG（Normalized Discounted Cumulative Gain）
- **指标名称**: `ndcg_score`
- **定义**: 考虑排序位置的相关性评分
- **计算**: 需要人工对每个结果打分（0-5 分）
- **目标值**: NDCG@10 > 0.8

## 2. 实现方案

### 2.1 日志埋点（推荐）

在 `SearchServiceImpl` 中添加结构化日志，便于后续分析。

**示例日志格式**：
```
SEARCH_METRIC|mode=hybrid|keyword=修仙|resultCount=20|totalHits=150|latency=245ms|vectorGenTime=180ms|knnOnly=5|bm25Only=8|both=7|pageNum=1|pageSize=20
```

### 2.2 前端埋点（用户行为）

在搜索结果页面，记录用户点击行为：

```javascript
// 前端代码示例（Vue）
function onSearchResultClick(bookId, position) {
  // 发送埋点事件
  fetch('/api/front/search/click', {
    method: 'POST',
    body: JSON.stringify({
      bookId: bookId,
      position: position, // 1-based
      keyword: currentKeyword,
      timestamp: Date.now()
    })
  });
}
```

### 2.3 监控面板（可选）

使用 Prometheus + Grafana 或 SkyWalking 可视化指标。

## 3. 快速评估方法（无需标注数据）

### 方法 1: 人工抽样评估
- 随机抽取 100 个真实搜索关键词
- 人工评估前 10 个结果的相关性（1-5 分）
- 计算平均分和 NDCG

### 方法 2: A/B 测试
- **对照组**: 纯文本搜索（BM25）
- **实验组**: 混合搜索（KNN + BM25）
- **对比指标**: CTR、转化率、平均点击位置

### 方法 3: 内部测试集
- 准备 50-100 个“标准查询-预期结果”对
- 例如：查询“修仙小说”，预期《斗破苍穹》应该在前 3 名
- 自动化测试，计算命中率

## 4. 代码实现建议

### 4.1 在 SearchServiceImpl 中添加指标记录

```java
// 在 searchBooks 方法中
long startTime = System.currentTimeMillis();
String searchMode = "text_only";

if (!StringUtils.isBlank(condition.getKeyword())) {
    try {
        searchMode = "hybrid";
        long vectorStartTime = System.currentTimeMillis();
        // ... 向量生成 ...
        long vectorTime = System.currentTimeMillis() - vectorStartTime;
        
        // ... 执行查询和合并 ...
        
        // 计算分布
        long knnOnly = combinedMap.values().stream()
            .filter(r -> r.knnScore > 0 && r.bm25Score == 0).count();
        long bm25Only = combinedMap.values().stream()
            .filter(r -> r.knnScore == 0 && r.bm25Score > 0).count();
        long both = combinedMap.values().stream()
            .filter(r -> r.knnScore > 0 && r.bm25Score > 0).count();
        
        long totalTime = System.currentTimeMillis() - startTime;
        
        // 记录指标
        log.info("SEARCH_METRIC|mode={}|keyword={}|resultCount={}|totalHits={}|latency={}ms|vectorGenTime={}ms|knnOnly={}|bm25Only={}|both={}|pageNum={}|pageSize={}", 
            searchMode,
            condition.getKeyword(), 
            list.size(),
            totalValue,
            totalTime,
            vectorTime,
            knnOnly,
            bm25Only,
            both,
            condition.getPageNum(),
            condition.getPageSize());
    }
}
```

### 4.2 创建搜索点击统计接口

在 `SearchController` 中添加：

```java
@PostMapping("click")
public RestResp<String> recordClick(@RequestBody SearchClickDto clickDto) {
    // 记录到数据库或日志
    log.info("SEARCH_CLICK|bookId={}|position={}|keyword={}|timestamp={}", 
        clickDto.getBookId(), 
        clickDto.getPosition(),
        clickDto.getKeyword(),
        clickDto.getTimestamp());
    return RestResp.ok("OK");
}
```

## 5. 评估周期建议

- **每日**: 监控响应时间、无结果率
- **每周**: 分析 CTR、转化率趋势
- **每月**: 人工抽样评估相关性，调整权重参数

## 6. 优化方向

根据指标反馈，可以调整：

1. **权重参数**: 如果 CTR 低，尝试调整 `vectorWeight` 和 `textWeight`
2. **召回数量**: 如果 `both_count` 太少，增加 `recallSize`
3. **向量模型**: 如果相关性差，考虑更换 Embedding 模型
4. **文本分词**: 如果 BM25 效果差，优化 IK 分词器配置
