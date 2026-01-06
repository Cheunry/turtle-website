# Novel AI 混合搜索 (Hybrid Search) 技术文档

本文档详细说明了在 `novel-search` 模块中集成的 AI 混合搜索功能。该方案结合了传统的 Elasticsearch 全文检索（BM25）和基于大模型（DashScope text-embedding-v4）的向量检索（KNN），通过 RRF（Rank Reciprocal Fusion）算法进行结果融合，以提供更精准、更语义化的搜索体验。

## 1. 架构概览

*   **向量化 (Indexing Phase)**:
    *   在书籍信息变更时（通过 RocketMQ 监听），系统调用 DashScope Embedding API 生成书籍的向量表示（基于书名、作者、简介）。
    *   向量数据随书籍元数据一同写入 Elasticsearch 索引。
*   **检索 (Search Phase)**:
    *   用户发起搜索请求。
    *   系统并行执行两路检索：
        1.  **文本检索**: 使用 BM25 算法匹配关键词（对书名、作者名加权）。
        2.  **向量检索**: 将用户查询词转换为向量，在 ES 中进行 KNN 近邻搜索。
    *   **结果融合**: 使用 RRF 算法将两路结果的排名进行融合，输出最终排序列表。

## 2. 核心组件与配置

### 2.1 依赖 (Maven)

在 `novel-search/pom.xml` 中引入了 Alibaba Cloud AI Starter：

```xml
<dependency>
    <groupId>com.alibaba.cloud.ai</groupId>
    <artifactId>spring-ai-alibaba-starter-dashscope</artifactId>
</dependency>
```

### 2.2 配置 (Nacos/Properties)

在 Nacos 配置中心（推荐）或配置文件中设置 DashScope API Key 和模型参数。

**Data ID**: `cipher-aes-ai-key.properties` (或其他加载的配置文件)

```properties
# DashScope API Key
spring.ai.dashscope.api-key=sk-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx

# 向量模型配置
# 使用 text-embedding-v4 (基于 Qwen3，支持自定义维度)
spring.ai.dashscope.embedding.options.model=text-embedding-v4
# 显式指定维度以匹配 ES 索引配置 (必须为 1536)
spring.ai.dashscope.embedding.options.dimensions=1536
```

### 2.3 Elasticsearch 索引 Mapping

索引名：`book`
关键变更：新增 `embedding` 字段。

```json
PUT /book
{
  "mappings": {
    "properties": {
      "id": { "type": "long" },
      // ... 其他基础字段 ...
      "bookName": { "type": "text", "analyzer": "ik_smart", "fields": { "keyword": { "type": "keyword" } } },
      "bookDesc": { "type": "text", "analyzer": "ik_max_word" },
      
      // --- 向量字段 ---
      "embedding": {
        "type": "dense_vector",
        "dims": 1536,          // 维度：必须与模型输出一致
        "index": true,         // 开启索引以支持 KNN
        "similarity": "cosine" // 相似度算法：余弦相似度
      }
    }
  }
}
```

## 3. 代码实现细节

### 3.1 监听器 (`BookChangeMqListener.java`)

负责处理书籍数据的变更同步。

*   **逻辑**:
    1.  接收 MQ 消息（书籍 ID）。
    2.  调用 Feign 获取书籍最新详情。
    3.  **向量生成**: 拼接 `书名 + 作者 + 简介`，截取前 2000 字符，调用 `embeddingModel.embed()` 生成向量。
    4.  如果向量生成失败（如网络抖动），**降级处理**：仅记录错误日志，继续将书籍的基础信息写入 ES，确保搜索功能的可用性（虽无向量但仍可被普通搜索检索）。
    5.  写入 Elasticsearch。

### 3.2 搜索服务 (`SearchServiceImpl.java`)

负责处理前端搜索请求。

*   **混合检索逻辑 (`searchBooks`)**:
    *   **条件构建**: 复用 `BoolQuery` 构建逻辑，处理分类、字数、状态等过滤条件。
    *   **分支判断**:
        *   **有关键词 (Keyword)**: 启用 Hybrid Search。
            1.  生成查询词向量。
            2.  构建 `knn` 查询（向量路），同时应用过滤条件 (`filter`)。
            3.  构建 `query` 查询（文本路，BM25）。
            4.  配置 `rank.rrf`，设置 `windowSize` 和 `rankConstant`。
            5.  **异常处理**: 若向量生成失败，自动降级为纯文本搜索。
        *   **无关键词**: 仅执行普通的 Filter 查询和基于数据库字段的排序（如按时间、热度）。

## 4. 注意事项与潜在风险

1.  **维度一致性 (Critical)**:
    *   Elasticsearch 的 `dense_vector` 维度 (`dims`) 必须与模型输出维度严格一致。
    *   `text-embedding-v4` 支持动态维度，因此必须在配置中显式指定 `dimensions=1536`，否则默认值可能（如 1024）导致 ES 写入报错。

2.  **RRF 与 排序 (Sorting)**:
    *   启用 RRF 后，ES 会忽略标准的 `sort` 参数。这是符合预期的，因为 RRF 的分数是基于排名的倒数，与传统的 TF-IDF 分数或时间戳无法直接比较。
    *   如果业务场景**强制要求**按时间排序（即使有关键词），则不能使用 RRF，而应改为使用 `script_score` 将向量分数与文本分数线性加权，但这需要精细调参。目前的 RRF 方案更适合“综合相关度”优先的场景。

3.  **成本控制**:
    *   向量生成涉及 API 调用费用。
    *   `BookChangeMqListener` 中的文本截断（2000字符）不仅是为了符合模型限制，也是为了控制 Token 消耗成本。

4.  **数据初始化**:
    *   上线前必须**重建索引**。
    *   重建索引后，旧数据没有向量。需要触发一次全量同步（遍历所有书籍 ID 发送 MQ 消息），以补充向量数据。

## 5. 常见问题排查

*   **ES 报错 `vector length expects: 1536, but got: 1024`**:
    *   原因：模型生成的维度与 ES Mapping 不匹配。
    *   解决：检查 Nacos 配置，确保 `spring.ai.dashscope.embedding.options.dimensions=1536` 生效。

*   **搜索结果不含新书**:
    *   原因：MQ 消费积压或向量生成失败。
    *   解决：查看 `novel-search` 日志，搜索 `[MQ]` 关键字确认消费情况；查看是否有 API 调用异常。

*   **启动报错 `NoClassDefFoundError`**:
    *   原因：Spring AI 依赖冲突或版本不兼容。
    *   解决：检查 Maven 依赖树，确保 `spring-ai-alibaba-starter-dashscope` 版本正确。

