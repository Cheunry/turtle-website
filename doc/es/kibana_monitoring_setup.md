# 使用 Kibana 监控搜索系统

本文档介绍如何使用 Kibana 监控和可视化搜索系统的各项指标。

## 1. 监控方案概述

### 1.1 监控架构

```
应用日志 (SEARCH_METRIC)
    ↓
Filebeat / Logstash (日志收集)
    ↓
Elasticsearch (存储日志)
    ↓
Kibana (可视化分析)
```

### 1.2 可监控的指标

**技术指标**：
- 搜索响应时间（latency）
- 向量生成耗时（vectorGenTime）
- 搜索结果数量（resultCount）
- 结果分布（knnOnly, bm25Only, both）

**业务指标**：
- 搜索模式分布（hybrid vs text_only）
- 热门搜索关键词
- 无结果率
- 搜索量趋势

## 2. 方案一：使用 Filebeat 收集日志（推荐）

### 2.1 安装 Filebeat

**Docker 方式**：
```yaml
# docker-compose.yml
filebeat:
  image: docker.elastic.co/beats/filebeat:8.10.4
  volumes:
    - ./filebeat.yml:/usr/share/filebeat/filebeat.yml:ro
    - ./logs:/var/log/novel-search:ro
    - /var/lib/docker/containers:/var/lib/docker/containers:ro
    - /var/run/docker.sock:/var/run/docker.sock:ro
  environment:
    - ELASTICSEARCH_HOSTS=http://elasticsearch:9200
    - KIBANA_HOST=http://kibana:5601
```

### 2.2 Filebeat 配置

创建 `filebeat.yml`：

```yaml
filebeat.inputs:
  - type: log
    enabled: true
    paths:
      - /var/log/novel-search/novel-search-service.log
    multiline.pattern: '^\d{4}-\d{2}-\d{2}'
    multiline.negate: true
    multiline.match: after
    fields:
      service: novel-search
      log_type: application
    fields_under_root: false
    include_lines: ['SEARCH_METRIC']

processors:
  - dissect:
      tokenizer: "%{timestamp} %{level} %{pid} --- [%{thread}] %{logger} : %{message}"
      field: "message"
      target_prefix: "parsed"
  
  - grok:
      match:
        parsed.message: 'SEARCH_METRIC\|mode=%{WORD:search_mode}\|keyword=%{DATA:keyword}\|resultCount=%{NUMBER:result_count:int}\|totalHits=%{NUMBER:total_hits:long}\|latency=%{NUMBER:latency_ms:int}ms\|vectorGenTime=%{NUMBER:vector_gen_time_ms:int}ms\|knnOnly=%{NUMBER:knn_only:int}\|bm25Only=%{NUMBER:bm25_only:int}\|both=%{NUMBER:both:int}\|pageNum=%{NUMBER:page_num:int}\|pageSize=%{NUMBER:page_size:int}'
      field: "parsed.message"
      target_field: "search_metric"
      ignore_missing: true

output.elasticsearch:
  hosts: ["http://elasticsearch:9200"]
  index: "novel-search-metrics-%{+yyyy.MM.dd}"

setup.template.settings:
  index.number_of_shards: 1
  index.codec: best_compression
```

### 2.3 启动 Filebeat

```bash
docker-compose up -d filebeat
```

## 3. 方案二：直接写入 Elasticsearch（简单方案）

如果不想使用 Filebeat，可以在应用代码中直接将指标写入 ES。

### 3.1 添加 Elasticsearch 指标写入

在 `SearchServiceImpl` 中添加：

```java
// 注入 ElasticsearchClient（已有）
private final ElasticsearchClient esClient;

// 在记录日志后，同时写入 ES
private void writeMetricToEs(String searchMode, String keyword, 
                              int resultCount, long totalHits, 
                              long latency, long vectorGenTime,
                              long knnOnly, long bm25Only, long both,
                              int pageNum, int pageSize) {
    try {
        Map<String, Object> metric = new HashMap<>();
        metric.put("timestamp", System.currentTimeMillis());
        metric.put("searchMode", searchMode);
        metric.put("keyword", keyword);
        metric.put("resultCount", resultCount);
        metric.put("totalHits", totalHits);
        metric.put("latency", latency);
        metric.put("vectorGenTime", vectorGenTime);
        metric.put("knnOnly", knnOnly);
        metric.put("bm25Only", bm25Only);
        metric.put("both", both);
        metric.put("pageNum", pageNum);
        metric.put("pageSize", pageSize);
        
        esClient.index(i -> i
            .index("novel-search-metrics")
            .document(metric)
        );
    } catch (Exception e) {
        log.warn("写入搜索指标到 ES 失败", e);
    }
}
```

**注意**：这种方式会增加搜索接口的延迟，建议异步写入。

## 4. Kibana Dashboard 配置

### 4.1 创建索引模式

1. 打开 Kibana → **Management** → **Stack Management** → **Index Patterns**
2. 创建索引模式：`novel-search-metrics-*`
3. 时间字段选择：`@timestamp` 或 `timestamp`

### 4.2 创建可视化图表

#### A. 搜索响应时间趋势图

**配置**：
- **可视化类型**: Line Chart
- **指标**: Average of `latency`
- **X 轴**: Date Histogram on `@timestamp` (Interval: 1 hour)
- **Y 轴**: Average latency (ms)

**KQL 查询**（可选）：
```kql
searchMode: hybrid
```

#### B. 向量生成耗时分布

**配置**：
- **可视化类型**: Histogram
- **指标**: Average of `vectorGenTime`
- **X 轴**: Date Histogram on `@timestamp`
- **Y 轴**: Average vector generation time (ms)

#### C. 搜索模式分布

**配置**：
- **可视化类型**: Pie Chart
- **指标**: Count
- **切片依据**: Terms on `searchMode`
  - hybrid
  - text_only

#### D. 结果分布（KNN vs BM25）

**配置**：
- **可视化类型**: Stacked Bar Chart
- **指标**: Sum of `knnOnly`, `bm25Only`, `both`
- **X 轴**: Date Histogram on `@timestamp`
- **Y 轴**: Stacked by field (knnOnly, bm25Only, both)

#### E. 热门搜索关键词

**配置**：
- **可视化类型**: Data Table
- **指标**: Count
- **分组依据**: Terms on `keyword.keyword` (Top 10)
- **排序**: Descending by count

#### F. 无结果率

**配置**：
- **可视化类型**: Metric
- **指标**: 
  - Total searches: Count
  - No result searches: Count where `totalHits = 0`
  - No result rate: `(No result searches / Total searches) × 100%`

**KQL 查询**：
```kql
totalHits: 0
```

### 4.3 创建 Dashboard

1. 打开 **Dashboard** → **Create Dashboard**
2. 添加上述所有可视化图表
3. 调整布局和大小
4. 设置自动刷新（如每 5 分钟）

## 5. 常用 Kibana 查询（KQL）

### 5.1 查询混合搜索的性能

```kql
searchMode: hybrid
AND latency > 500
```

### 5.2 查询向量生成慢的请求

```kql
vectorGenTime > 300
```

### 5.3 查询结果分布异常的情况

```kql
(both: 0 AND knnOnly: 0) OR (both: 0 AND bm25Only: 0)
```

### 5.4 查询热门关键词（最近 24 小时）

```kql
@timestamp >= now()-24h
```

然后按 `keyword.keyword` 分组统计。

## 6. 告警配置（可选）

### 6.1 使用 Kibana Alerting

**告警规则示例**：

1. **响应时间告警**：
   - **条件**: Average latency > 1000ms (最近 5 分钟)
   - **动作**: 发送邮件/钉钉通知

2. **无结果率告警**：
   - **条件**: No result rate > 10% (最近 10 分钟)
   - **动作**: 发送告警通知

3. **向量生成失败告警**：
   - **条件**: vectorGenTime = 0 AND searchMode = hybrid (最近 5 分钟)
   - **动作**: 发送告警通知

## 7. 性能监控 Dashboard 示例

### 7.1 实时监控面板

**布局**：
```
┌─────────────────┬─────────────────┐
│   响应时间趋势    │   向量生成耗时    │
├─────────────────┼─────────────────┤
│   搜索模式分布    │   结果分布统计    │
├─────────────────┴─────────────────┤
│        热门搜索关键词 Top 10        │
└───────────────────────────────────┘
```

### 7.2 业务分析面板

**布局**：
```
┌─────────────────┬─────────────────┐
│   搜索量趋势      │   无结果率       │
├─────────────────┼─────────────────┤
│   平均点击位置    │   搜索转化率     │
└─────────────────┴─────────────────┘
```

## 8. 快速开始（简化版）

如果你只是想快速查看搜索指标，可以：

### 8.1 在 Kibana Dev Tools 中查询

```json
GET novel-search-metrics-*/_search
{
  "size": 0,
  "aggs": {
    "avg_latency": {
      "avg": { "field": "latency" }
    },
    "avg_vector_time": {
      "avg": { "field": "vectorGenTime" }
    },
    "search_mode_distribution": {
      "terms": { "field": "searchMode.keyword" }
    },
    "top_keywords": {
      "terms": {
        "field": "keyword.keyword",
        "size": 10
      }
    }
  }
}
```

### 8.2 使用 Discover 查看原始日志

1. 打开 **Discover**
2. 选择索引模式：`novel-search-metrics-*`
3. 使用 KQL 过滤：`message: "SEARCH_METRIC"`
4. 查看时间序列和字段值

## 9. 注意事项

1. **索引生命周期管理**：
   - 建议设置索引保留期（如 30 天）
   - 使用 ILM（Index Lifecycle Management）自动删除旧数据

2. **性能影响**：
   - 如果使用方案二（直接写入 ES），建议异步写入
   - 避免在高并发时阻塞搜索请求

3. **数据量控制**：
   - 考虑采样（如只记录 10% 的请求）
   - 或者只记录异常请求（latency > 阈值）

## 10. 推荐配置

**生产环境推荐**：
- 使用 **Filebeat** 收集日志（方案一）
- 异步写入，不阻塞主流程
- 设置索引保留期 30 天
- 配置告警规则，及时发现问题

**开发/测试环境**：
- 可以直接使用 Kibana Dev Tools 查询
- 或者使用方案二（简单直接）

