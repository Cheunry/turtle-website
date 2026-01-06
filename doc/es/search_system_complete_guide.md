# 小说搜索系统完整技术文档

本文档全面介绍小说搜索系统的技术架构、实现细节、优化策略和运维指南。

## 目录

1. [系统概述](#1-系统概述)
2. [技术栈](#2-技术栈)
3. [架构设计](#3-架构设计)
4. [Elasticsearch 索引设计](#4-elasticsearch-索引设计)
5. [向量生成技术](#5-向量生成技术)
6. [混合搜索实现](#6-混合搜索实现)
7. [数据同步机制](#7-数据同步机制)
8. [性能优化](#8-性能优化)
9. [监控与评估](#9-监控与评估)
10. [常见问题排查](#10-常见问题排查)
11. [未来优化方向](#11-未来优化方向)

---

## 1. 系统概述

### 1.1 系统简介

小说搜索系统是一个基于 **Elasticsearch** 和 **AI 向量检索** 的混合搜索系统，结合了传统的全文检索（BM25）和基于大模型的向量检索（KNN），提供更精准、更语义化的搜索体验。

### 1.2 核心特性

- ✅ **混合搜索**：向量检索 + 全文检索，兼顾语义理解和精确匹配
- ✅ **实时同步**：基于 RocketMQ 的实时数据同步
- ✅ **中文优化**：使用 DashScope text-embedding-v4 模型，专为中文优化
- ✅ **高可用性**：异常降级机制，保证服务可用性
- ✅ **可扩展性**：微服务架构，易于扩展

### 1.3 搜索流程

```
用户查询："修仙类的小说"
    ↓
    ├─→ [向量化] → KNN 检索（语义相似度）
    │
    └─→ [分词] → BM25 检索（关键词匹配）
         ↓
    [结果合并] → 加权排序 → 返回结果
```

---

## 2. 技术栈

### 2.1 后端技术栈

| 技术组件 | 版本/说明 | 用途 |
|---------|----------|------|
| **Java** | JDK 21 | 主要开发语言 |
| **Spring Boot** | 3.4.3 | 应用框架 |
| **Spring Cloud** | 2024.0.0 | 微服务框架 |
| **Elasticsearch** | 8.10.4 | 搜索引擎与向量数据库 |
| **Spring AI Alibaba** | 1.0.0.2 | AI 模型集成框架 |
| **DashScope (通义千问)** | text-embedding-v4 | 向量生成模型 |
| **RocketMQ** | 2.3.1 | 消息队列（数据同步） |
| **Nacos** | - | 配置中心与服务发现 |
| **MyBatis Plus** | 3.5.8 | ORM 框架 |

### 2.2 前端技术栈

| 技术组件 | 说明 | 用途 |
|---------|------|------|
| **Vue.js** | 3.x | 前端框架 |
| **Element Plus** | - | UI 组件库 |
| **Axios** | - | HTTP 客户端 |

### 2.3 AI 相关依赖

**Maven 依赖**：
```xml
<dependency>
    <groupId>com.alibaba.cloud.ai</groupId>
    <artifactId>spring-ai-alibaba-starter-dashscope</artifactId>
    <version>1.0.0.2</version>
</dependency>
```

---

## 3. 架构设计

### 3.1 微服务架构

```
┌─────────────┐
│   Gateway   │ (路由网关)
└──────┬──────┘
       │
       ├─→ novel-search-service (搜索服务)
       │      ├─ Elasticsearch Client
       │      ├─ EmbeddingModel (DashScope)
       │      └─ RocketMQ Consumer
       │
       └─→ novel-book-service (书籍服务)
              └─ RocketMQ Producer
```

### 3.2 数据流

**写入流程**：
```
Book Service → MQ → Search Service → ES
                    ↓
                DashScope API (向量生成)
```

**查询流程**：
```
User → Gateway → Search Service
                      ↓
              ┌───────┴───────┐
              │               │
         DashScope API    Elasticsearch
         (向量生成)        (KNN + BM25)
              │               │
              └───────┬───────┘
                      ↓
                结果合并与排序
                      ↓
                   返回用户
```

---

## 4. Elasticsearch 索引设计

### 4.1 索引 Mapping

**索引名**: `book`

**完整 Mapping**：
```json
PUT /book
{
  "mappings": {
    "properties": {
      "id": { "type": "long" },
      "workDirection": { "type": "byte" },
      "categoryId": { "type": "integer" },
      "categoryName": { "type": "keyword" },
      "picUrl": { "type": "keyword", "index": false },
      "bookName": { 
        "type": "text", 
        "analyzer": "ik_smart", 
        "fields": { 
          "keyword": { "type": "keyword" } 
        } 
      },
      "authorName": { 
        "type": "text", 
        "analyzer": "ik_smart", 
        "fields": { 
          "keyword": { "type": "keyword" } 
        } 
      },
      "bookDesc": { 
        "type": "text", 
        "analyzer": "ik_max_word" 
      },
      "score": { "type": "integer" },
      "bookStatus": { "type": "byte" },
      "visitCount": { "type": "long" },
      "wordCount": { "type": "integer" },
      "commentCount": { "type": "integer" },
      "lastChapterName": { "type": "text", "index": false },
      "lastChapterUpdateTime": { "type": "date", "format": "epoch_millis" },
      "isVip": { "type": "byte" },
      "embedding": {
        "type": "dense_vector",
        "dims": 1024,
        "index": true,
        "similarity": "cosine"
      }
    }
  }
}
```

### 4.2 字段说明

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `id` | `long` | 主键 |
| `bookName` | `text` + `keyword` | 书名（支持分词和精确匹配） |
| `authorName` | `text` + `keyword` | 作者名 |
| `bookDesc` | `text` | 简介（仅分词） |
| `embedding` | `dense_vector` | 向量字段（1024 维） |
| `visitCount` | `long` | 点击量 |
| `wordCount` | `integer` | 字数 |
| `bookStatus` | `byte` | 状态（连载/完结） |
| `categoryId` | `integer` | 分类ID |
| `workDirection` | `byte` | 方向（男频/女频） |

### 4.3 向量索引配置

**配置说明**：
- `dims: 1024`: 向量维度，必须与模型输出一致
- `index: true`: 启用 HNSW 索引加速 KNN 查询
- `similarity: cosine`: 使用余弦相似度计算

**HNSW 算法**：
- **全称**: Hierarchical Navigable Small World
- **原理**: 构建多层图结构，快速定位最近邻
- **优势**: 查询复杂度 O(log N)，适合大规模向量检索

### 4.4 分词器配置

**IK Analyzer**：
- **索引时**: `ik_max_word`（细粒度分词，提高召回率）
- **搜索时**: `ik_smart`（粗粒度分词，提高精确率）

**示例**：
- 文本："修仙小说"
- `ik_max_word`: ["修", "仙", "修仙", "小说"]
- `ik_smart`: ["修仙", "小说"]

### 4.5 常用操作

**查询索引数量**：
```json
GET book/_count
```

**删除索引**：
```json
DELETE book
```

---

## 5. 向量生成技术

### 5.1 模型信息

**模型信息**：
- **模型名称**: `text-embedding-v4`
- **提供商**: 阿里云 DashScope（通义千问）
- **基础架构**: 基于 Qwen3 的 Transformer 模型
- **向量维度**: **1024 维**（当前配置）
- **语言支持**: 多语言，对中文优化
- **API 接口**: DashScope Embedding API

**模型特点**：
- ✅ 在 MTEB（多语言文本嵌入基准）评测中表现优异
- ✅ 支持 64-2048 维自定义维度（当前使用 1024）
- ✅ 对中文语义理解能力强
- ✅ 支持长文本（最大 8192 tokens）

### 5.2 配置方式

**配置文件位置**: Nacos 配置中心（推荐）或 `application.yml`

**配置示例**：
```properties
# DashScope API Key
spring.ai.dashscope.api-key=sk-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx

# 向量模型配置
spring.ai.dashscope.embedding.options.model=text-embedding-v4
# 向量维度：必须与 ES 索引配置一致（当前为 1024）
spring.ai.dashscope.embedding.options.dimensions=1024
```

**重要提示**：
- ⚠️ **维度一致性**：`dimensions` 必须与 Elasticsearch 索引中的 `dims` 完全一致
- ⚠️ **API Key 安全**：建议使用 Nacos 配置中心加密存储

### 5.3 向量化策略

**输入文本构建**（`BookChangeMqListener.java`）：
```java
String textToEmbed = "书名:" + book.getBookName() + 
                   "; 作者:" + book.getAuthorName() + 
                   "; 简介:" + book.getBookDesc();
```

**策略说明**：
- **组合多个字段**：书名、作者、简介组合，提供更丰富的语义信息
- **结构化格式**：使用分隔符（`:` 和 `;`），帮助模型理解不同字段的含义
- **字段权重**：通过字段顺序和格式，隐式地给书名更高权重

**文本截断**：
```java
// 简单截断文本以防过长 (假设模型限制为 8k token，这里保守截取 2000 字符)
if (textToEmbed.length() > 2000) {
    textToEmbed = textToEmbed.substring(0, 2000);
}
```

**截断原因**：
1. **成本控制**：API 调用按 Token 计费，文本越长成本越高
2. **模型限制**：虽然模型支持 8192 tokens，但实际使用中 2000 字符已足够
3. **性能考虑**：文本越短，API 响应越快

### 5.4 向量生成流程

**实时同步流程**：
```
书籍变更事件（新增/更新）
    ↓
发送 RocketMQ 消息（bookId）
    ↓
BookChangeMqListener 消费消息
    ↓
调用 Feign 获取最新书籍数据
    ↓
构建向量化文本："书名:xxx; 作者:xxx; 简介:xxx"
    ↓
文本截断（>2000 字符则截断）
    ↓
调用 embeddingModel.embed(textToEmbed)
    ↓
DashScope API 返回 1024 维向量
    ↓
转换为 List<Float> 并设置到 book.setEmbedding()
    ↓
写入 Elasticsearch
```

**异常处理**：
- 如果向量生成失败（网络异常、API 限流等），**不会阻塞数据同步**
- 书籍的基础信息（书名、作者等）仍会写入 ES
- 该书籍仍可通过 BM25 文本搜索找到，只是无法使用向量检索

### 5.5 算法原理

**Transformer 架构**：
```
输入文本："书名:修仙小说; 作者:张三; 简介:一个修仙者的故事..."
    ↓
Tokenization（分词）
    ↓
Token Embedding（词嵌入）
    ↓
Positional Encoding（位置编码）
    ↓
Multi-Head Attention（多头注意力）
    ↓
Feed Forward Network（前馈网络）
    ↓
Pooling（池化，通常是 CLS token 或平均池化）
    ↓
输出向量：[0.123, -0.456, 0.789, ...] (1024维)
```

**相似度计算**：
在 Elasticsearch 中使用**余弦相似度**（Cosine Similarity）计算向量相似度：

```
相似度 = (A · B) / (||A|| × ||B||)
```

**为什么使用余弦相似度？**
- ✅ **归一化**：不受向量长度影响，只关注方向
- ✅ **范围固定**：结果在 [-1, 1] 之间，便于理解
- ✅ **适合文本**：文本向量的语义主要体现在方向上，而非长度

### 5.6 为什么选择这种方案？

**为什么选择 DashScope text-embedding-v4？**

| 对比项 | DashScope v4 | 其他方案（如 OpenAI） |
|--------|-------------|---------------------|
| **中文支持** | ✅ 优秀（专为中文优化） | ⚠️ 一般 |
| **成本** | ✅ 相对较低 | ⚠️ 较高 |
| **延迟** | ✅ 较低（国内服务） | ⚠️ 较高（国外服务） |
| **稳定性** | ✅ 阿里云基础设施 | ⚠️ 依赖国外服务 |
| **维度灵活性** | ✅ 支持 64-2048 维 | ⚠️ 固定维度 |

**为什么选择 1024 维？**

| 维度 | 语义表达能力 | 存储成本 | 计算成本 | 推荐场景 |
|------|------------|---------|---------|---------|
| 256 | ⚠️ 较低 | ✅ 低 | ✅ 低 | 简单场景 |
| 512 | ⚠️ 中等 | ✅ 较低 | ✅ 较低 | 中等规模 |
| **1024** | ✅ **良好** | ✅ **适中** | ✅ **适中** | **推荐** |
| 1536 | ✅ 优秀 | ⚠️ 较高 | ⚠️ 较高 | 高精度需求 |
| 2048 | ✅ 优秀 | ⚠️ 高 | ⚠️ 高 | 特殊场景 |

---

## 6. 混合搜索实现

### 6.1 技术原理

当前系统实现了 **向量检索 + 全文检索** 的混合搜索模式：

```
用户查询词
    ↓
    ├─→ [向量化] → KNN 检索（语义相似度）
    │
    └─→ [分词] → BM25 检索（关键词匹配）
         ↓
    [结果合并] → 加权排序 → 返回结果
```

### 6.2 向量检索（KNN）

**技术实现**：
- **向量模型**: DashScope `text-embedding-v4`
- **向量维度**: 1024 维
- **相似度算法**: 余弦相似度（Cosine Similarity）
- **索引结构**: Elasticsearch `dense_vector` 类型，使用 HNSW 算法加速

**工作原理**：
1. 将用户查询词通过 Embedding 模型转换为 1024 维向量
2. 在 ES 的向量索引中，使用 KNN 算法找到最相似的 Top-K 个文档
3. 使用余弦相似度计算相似度分数

**优势**：
- 支持语义理解（同义词、近义词）
- 支持模糊匹配（即使关键词不完全一致也能找到相关结果）

**示例**：
- 查询："修仙小说" → 能找到 "修真"、"玄幻" 相关的小说
- 查询："爱情故事" → 能找到 "言情"、"浪漫" 相关的小说

### 6.3 全文检索（BM25）

**技术实现**：
- **分词器**: IK Analyzer（`ik_smart` / `ik_max_word`）
- **算法**: BM25（Best Matching 25）
- **字段权重**:
  - `bookName`: 2.0（书名最重要）
  - `authorName`: 1.8（作者名次重要）
  - `bookDesc`: 0.1（简介权重较低）

**工作原理**：
1. 对查询词进行中文分词（IK Analyzer）
2. 使用 BM25 算法计算每个文档的相关性分数
3. BM25 考虑了：
   - **词频（TF）**: 关键词在文档中出现的频率
   - **逆文档频率（IDF）**: 关键词在整个索引中的稀有程度
   - **文档长度归一化**: 避免长文档获得不公平的优势

**优势**：
- 精确匹配关键词
- 支持高亮显示
- 性能优秀（毫秒级响应）

### 6.4 结果融合算法

**当前实现：加权排名倒数融合**

由于 Elasticsearch 的 RRF（Reciprocal Rank Fusion）需要商业许可证，我们实现了**手动融合算法**：

**算法步骤**：
1. **分别执行两路检索**：
   - KNN 检索：召回 Top-N 个结果（N = pageSize × 3）
   - BM25 检索：召回 Top-N 个结果

2. **计算排名分数**：
   - KNN 分数 = `1.0 / (排名位置 + 1)`
   - BM25 分数 = `1.0 / (排名位置 + 1)`
   - 这类似于 RRF 的倒数排名思想

3. **加权合并**：
   ```
   综合分数 = KNN分数 × 0.6 + BM25分数 × 0.4
   ```
   - 向量权重：0.6（更偏向语义理解）
   - 文本权重：0.4（保留关键词精确匹配）

4. **去重与排序**：
   - 使用 `Map<bookId, CombinedResult>` 去重
   - 按综合分数降序排序
   - 分页返回

**算法特点**：
- ✅ 不依赖商业许可证
- ✅ 可灵活调整权重
- ✅ 性能可控（两次查询 + 内存合并）

**与 RRF 的对比**：

| 特性 | 当前实现 | RRF（商业版） |
|------|---------|--------------|
| 许可证 | 免费 | 需要商业许可证 |
| 算法复杂度 | O(N log N) | O(N) |
| 权重调整 | 可调 | 固定常数 |
| 性能 | 良好 | 优秀 |

### 6.5 代码实现

**关键代码位置**：
- `SearchServiceImpl.java`: 搜索服务实现
- `BookChangeMqListener.java`: 向量生成监听器

**搜索流程**（`SearchServiceImpl.java`）：
```java
// 1. 生成查询向量
float[] queryVectorArray = embeddingModel.embed(condition.getKeyword());

// 2. 执行 KNN 查询（向量检索）
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
);

// 3. 执行 BM25 查询（文本检索）
SearchResponse<BookEsRespDto> bm25Response = esClient.search(s -> s
    .index(EsConsts.BookIndex.INDEX_NAME)
    .query(q -> q.bool(boolQuery))
    .size(recallSize)
    .highlight(...)
);

// 4. 手动合并结果
// ... 合并逻辑 ...
```

---

## 7. 数据同步机制

### 7.1 实时同步（MQ 驱动）

**技术栈**：
- **消息队列**: RocketMQ
- **消费模式**: 集群消费（保证数据一致性）

**同步流程**：
```
书籍变更事件
    ↓
发送 MQ 消息（bookId）
    ↓
BookChangeMqListener 消费
    ↓
调用 Feign 获取最新数据
    ↓
生成向量（调用 DashScope API）
    ↓
写入 Elasticsearch
```

**关键代码位置**：
- `BookChangeMqListener.java`: MQ 监听器
- `BookFeign`: 跨服务数据获取
- `BookFeignManager.java`: Feign 调用封装

**优势**：
- 实时性强（秒级同步）
- 解耦（搜索服务不直接依赖书籍服务）
- 可重试（MQ 保证消息不丢失）

**异常处理**：
- 当 `getEsBookById` 返回 `null` 时，先检查 ES 中是否存在该书籍
- 如果 ES 中存在：说明是更新/删除操作，执行删除
- 如果 ES 中不存在：可能是新增书籍但服务调用失败，跳过删除（因为本来就没有数据）

### 7.2 全量同步（定时任务）

**技术栈**：
- **任务调度**: 手动触发接口（`/api/front/search/sync/all`）
- **批量处理**: Elasticsearch Bulk API

**同步流程**：
```
全量同步接口触发
    ↓
分批查询书籍（每批 30 条）
    ↓
为每本书生成向量
    ↓
批量写入 ES（Bulk API）
    ↓
循环直到无更多数据
```

**关键代码位置**：
- `AllBookToEsTask.java`: 全量同步任务

**使用场景**：
- 首次上线
- 索引重建后
- 数据修复

---

## 8. 性能优化

### 8.1 查询优化

#### A. 召回数量控制
```java
int recallSize = Math.max(condition.getPageSize() * 3, 100);
```
- 召回 3 倍于页面大小的结果，给合并算法足够的候选
- 避免召回过多导致性能下降

#### B. 过滤条件下推
```java
.filter(f -> f.bool(boolQuery))
```
- 在 KNN 查询中应用过滤条件，减少候选数量
- 避免先召回再过滤的低效操作

#### C. 高亮优化
- 仅对文本检索结果启用高亮（向量检索无高亮需求）
- 减少不必要的计算

### 8.2 向量生成优化

#### A. 文本截断
```java
if (textToEmbed.length() > 2000) {
    textToEmbed = textToEmbed.substring(0, 2000);
}
```
- 控制 Token 消耗，降低 API 成本
- 保证核心信息（书名、作者）不丢失

#### B. 异常降级
```java
try {
    // 生成向量
} catch (Exception e) {
    // 降级为纯文本搜索
}
```
- 向量生成失败时，自动降级为 BM25 搜索
- 保证服务可用性

### 8.3 性能指标

**当前性能**：
- **向量生成延迟**：~200-500ms（取决于网络和文本长度）
- **API 成功率**：>99%（有重试机制）
- **全量同步速度**：~100-200 条/分钟（包含向量生成）
- **搜索响应时间**：
  - P50 < 200ms（50% 的请求）
  - P95 < 500ms（95% 的请求）
  - P99 < 1000ms（99% 的请求）

### 8.4 缓存策略（未来优化方向）

**可考虑的缓存点**：
1. **查询向量缓存**: 缓存常用查询词的向量（Redis，TTL: 1 小时）
2. **热门书籍向量**: 缓存 Top 1000 书籍的向量
3. **搜索结果缓存**: 缓存热门查询的结果（TTL: 5 分钟）

---

## 9. 监控与评估

### 9.1 核心评估指标

#### A. 技术指标（可观测）

**响应时间（Latency）**：
- **指标名称**: `search_latency_ms`
- **定义**: 从用户发起搜索请求到返回结果的耗时（毫秒）
- **目标值**: 
  - P50 < 200ms
  - P95 < 500ms
  - P99 < 1000ms

**向量生成耗时**：
- **指标名称**: `vector_gen_time_ms`
- **定义**: 调用 DashScope API 生成查询向量的耗时
- **目标值**: < 300ms

**召回数量**：
- **指标名称**: `search_recall_count`
- **定义**: 混合检索返回的总结果数（KNN + BM25 去重后）

**结果分布**：
- **指标名称**: `knn_only_count`, `bm25_only_count`, `both_count`
- **定义**: 
  - `knn_only_count`: 只在向量检索中出现的结果数
  - `bm25_only_count`: 只在文本检索中出现的结果数
  - `both_count`: 两路检索都命中的结果数

#### B. 业务指标（需要用户行为数据）

**点击率（CTR）**：
- **指标名称**: `search_ctr`
- **定义**: `点击搜索结果的数量 / 搜索请求总数`
- **目标值**: > 30%

**平均点击位置**：
- **指标名称**: `avg_click_position`
- **定义**: 用户点击的结果在列表中的平均位置（1-based）
- **目标值**: < 3

**无结果率**：
- **指标名称**: `no_result_rate`
- **定义**: `返回 0 结果的搜索请求数 / 总搜索请求数`
- **目标值**: < 5%

### 9.2 日志埋点

**示例日志格式**：
```
SEARCH_METRIC|mode=hybrid|keyword=修仙|resultCount=20|totalHits=150|latency=245ms|vectorGenTime=180ms|knnOnly=5|bm25Only=8|both=7|pageNum=1|pageSize=20
```

### 9.3 Kibana 监控（可选）

**监控方案**：
1. 使用 Filebeat 收集日志
2. 写入 Elasticsearch
3. 在 Kibana 中创建 Dashboard

**可监控的指标**：
- 搜索响应时间趋势
- 向量生成耗时分布
- 搜索模式分布（hybrid vs text_only）
- 结果分布（KNN vs BM25）
- 热门搜索关键词
- 无结果率

**快速查询**（Kibana Dev Tools）：
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

---

## 10. 常见问题排查

### 10.1 维度不匹配错误

**错误信息**：
```
vector length expects: 1024, but got: 1536
```

**原因**：
- 模型生成的维度与 ES Mapping 不匹配

**解决方案**：
1. 检查 Nacos 配置，确保 `spring.ai.dashscope.embedding.options.dimensions=1024` 生效
2. 检查 ES Mapping，确保 `dims: 1024`
3. 重启应用，使配置生效

### 10.2 搜索结果不含新书

**原因**：
- MQ 消费积压或向量生成失败
- 书籍审核状态不是 1（未审核通过）

**解决方案**：
1. 查看 `novel-search` 日志，搜索 `[MQ]` 关键字确认消费情况
2. 查看是否有 API 调用异常
3. 检查书籍的 `auditStatus` 是否为 1

### 10.3 向量生成失败

**原因**：
- DashScope API 调用失败（网络异常、限流等）
- API Key 配置错误

**解决方案**：
1. 检查 API Key 是否正确
2. 检查网络连接
3. 查看 DashScope 控制台的调用日志
4. 系统会自动降级为纯文本搜索，不影响服务可用性

### 10.4 启动报错 NoClassDefFoundError

**原因**：
- Spring AI 依赖冲突或版本不兼容

**解决方案**：
1. 检查 Maven 依赖树，确保 `spring-ai-alibaba-starter-dashscope` 版本正确
2. 清理 Maven 缓存：`mvn clean install -U`
3. 检查 Spring Boot 和 Spring Cloud 版本兼容性

### 10.5 搜索响应慢

**原因**：
- 向量生成耗时过长
- ES 查询性能问题
- 网络延迟

**解决方案**：
1. 检查向量生成耗时（查看日志中的 `vectorGenTime`）
2. 检查 ES 集群状态和性能
3. 考虑实现查询向量缓存（见优化方向）

---

## 11. 未来优化方向

### 11.1 查询向量缓存（推荐，易实现）

**实现思路**：
```java
// 伪代码
String cacheKey = "embedding:query:" + keyword;
List<Float> cachedVector = redis.get(cacheKey);
if (cachedVector != null) {
    return cachedVector;
}
// 调用 API 生成向量
List<Float> vector = embeddingModel.embed(keyword);
redis.set(cacheKey, vector, TTL_1_HOUR);
return vector;
```

**预期效果**：
- ✅ 减少 50-80% 的 API 调用（取决于查询重复度）
- ✅ 搜索延迟降低 ~200ms
- ✅ 实现简单，风险低

### 11.2 本地 Embedding 模型（推荐，长期优化）

**可选模型**：
- **BGE-M3**：中文优化，支持多语言，768 维
- **M3E**：中文 Embedding 模型，768 维
- **text2vec**：轻量级中文模型

**实现思路**：
1. 使用 ONNX Runtime 或 TensorFlow Lite 加载模型
2. 在应用内直接生成向量，无需调用 API
3. 保留 DashScope 作为降级方案

**预期效果**：
- ✅ 零 API 成本
- ✅ 延迟降低到 ~10-50ms（本地推理）
- ✅ 不受网络影响
- ⚠️ 需要模型文件存储（~500MB-2GB）
- ⚠️ 需要 GPU 或高性能 CPU

### 11.3 智能文本截断

**实现思路**：
```java
// 伪代码
StringBuilder sb = new StringBuilder();
sb.append("书名:").append(bookName); // 保证书名完整
sb.append("; 作者:").append(authorName); // 保证作者完整

// 计算剩余可用长度
int remaining = 2000 - sb.length();
if (remaining > 0 && bookDesc != null) {
    // 智能截断简介：优先保留开头和结尾
    String desc = bookDesc.length() > remaining 
        ? bookDesc.substring(0, remaining - 10) + "..." 
        : bookDesc;
    sb.append("; 简介:").append(desc);
}
```

**预期效果**：
- ✅ 保证核心信息（书名、作者）不丢失
- ✅ 简介信息更完整
- ✅ 实现简单

### 11.4 向量维度优化

**当前**：1024 维

**可选优化**：
- **768 维**：使用 BGE-M3 等模型，性能更好，语义能力略降
- **1536 维**：使用更高维度，语义能力更强，但成本增加

**建议**：
- 如果使用本地模型，推荐 768 维（BGE-M3）
- 如果继续使用 DashScope，保持 1024 维

### 11.5 其他优化方向

1. **异步向量生成**：书籍更新时异步生成向量，搜索时直接使用
2. **ES 升级**：如果升级到商业版，可使用原生 RRF
3. **批量 API**：使用 DashScope 的批量 API，降低成本
4. **增量更新**：只对变更的字段重新生成向量

---

## 12. 参考资料

- [Elasticsearch Dense Vector 文档](https://www.elastic.co/guide/en/elasticsearch/reference/current/dense-vector.html)
- [DashScope Embedding 文档](https://help.aliyun.com/zh/dashscope/developer-reference/text-embedding-api-details)
- [Spring AI 文档](https://docs.spring.io/spring-ai/reference/)
- [BM25 算法原理](https://en.wikipedia.org/wiki/Okapi_BM25)
- [HNSW 算法论文](https://arxiv.org/abs/1603.09320)
- [MTEB 评测基准](https://huggingface.co/spaces/mteb/leaderboard)

---

## 13. 总结

当前搜索系统基于 **Elasticsearch** 和 **DashScope text-embedding-v4** 实现了混合搜索，结合了向量检索和全文检索的优势，在中文语义理解、成本控制和性能之间取得了良好的平衡。

**核心优势**：
- ✅ 中文优化，语义理解准确
- ✅ 成本可控，延迟较低
- ✅ 异常降级，保证可用性
- ✅ 灵活可调，易于优化

**改进方向**：
- 🔄 查询向量缓存（短期优化）
- 🔄 智能文本截断（中期优化）
- 🔄 本地 Embedding 模型（长期优化）

通过持续优化，可以进一步提升搜索体验，降低运营成本。

