# 搜索系统技术栈与核心技术文档

本文档详细介绍了当前小说搜索系统使用的核心技术、算法原理和架构设计。

## 1. 技术栈概览

### 1.1 后端技术栈

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

### 1.2 前端技术栈

| 技术组件 | 说明 | 用途 |
|---------|------|------|
| **Vue.js** | 3.x | 前端框架 |
| **Element Plus** | - | UI 组件库 |
| **Axios** | - | HTTP 客户端 |

## 2. 核心搜索技术

### 2.1 混合检索（Hybrid Search）

#### 2.1.1 技术原理

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

#### 2.1.2 向量检索（KNN - K-Nearest Neighbors）

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

#### 2.1.3 全文检索（BM25）

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

### 2.2 结果融合算法

#### 2.2.1 当前实现：加权排名倒数融合

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

### 2.3 向量生成技术

#### 2.3.1 Embedding 模型

**模型信息**：
- **名称**: `text-embedding-v4`
- **提供商**: 阿里云 DashScope（通义千问）
- **基础模型**: Qwen3
- **向量维度**: 1024
- **语言支持**: 多语言（中文优化）

**模型特点**：
- 基于 Transformer 架构
- 在 MTEB（多语言文本嵌入基准）评测中表现优异
- 支持 64-2048 维自定义维度（当前使用 1024）

#### 2.3.2 向量化策略

**输入文本构建**：
```java
String textToEmbed = "书名:" + bookName + 
                   "; 作者:" + authorName + 
                   "; 简介:" + bookDesc;
```

**策略说明**：
- 组合书名、作者、简介，提供更丰富的语义信息
- 文本截断：限制在 2000 字符以内（控制 Token 消耗）
- 结构化格式：使用分隔符，帮助模型理解不同字段

**优化点**：
- 书名权重最高（用户最关注）
- 作者名次之（有助于区分同名作品）
- 简介补充语义信息

## 3. 数据同步架构

### 3.1 实时同步（MQ 驱动）

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

**优势**：
- 实时性强（秒级同步）
- 解耦（搜索服务不直接依赖书籍服务）
- 可重试（MQ 保证消息不丢失）

### 3.2 全量同步（定时任务）

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

## 4. Elasticsearch 索引设计

### 4.1 索引结构

**索引名**: `book`

**字段类型**：

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

### 4.2 向量索引配置

```json
{
  "embedding": {
    "type": "dense_vector",
    "dims": 1024,
    "index": true,
    "similarity": "cosine"
  }
}
```

**配置说明**：
- `dims: 1024`: 向量维度，必须与模型输出一致
- `index: true`: 启用 HNSW 索引加速 KNN 查询
- `similarity: cosine`: 使用余弦相似度计算

**HNSW 算法**：
- **全称**: Hierarchical Navigable Small World
- **原理**: 构建多层图结构，快速定位最近邻
- **优势**: 查询复杂度 O(log N)，适合大规模向量检索

### 4.3 分词器配置

**IK Analyzer**：
- **索引时**: `ik_max_word`（细粒度分词，提高召回率）
- **搜索时**: `ik_smart`（粗粒度分词，提高精确率）

**示例**：
- 文本："修仙小说"
- `ik_max_word`: ["修", "仙", "修仙", "小说"]
- `ik_smart`: ["修仙", "小说"]

## 5. 性能优化策略

### 5.1 查询优化

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

### 5.2 向量生成优化

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

### 5.3 缓存策略（未来优化方向）

**可考虑的缓存点**：
1. **查询向量缓存**: 缓存常用查询词的向量（Redis）
2. **热门书籍向量**: 缓存 Top 1000 书籍的向量
3. **搜索结果缓存**: 缓存热门查询的结果（TTL: 5 分钟）

## 6. 架构设计

### 6.1 微服务架构

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

### 6.2 数据流

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

## 7. 关键技术决策

### 7.1 为什么选择手动融合而非 RRF？

**原因**：
1. **许可证限制**: RRF 需要 Elasticsearch 商业许可证
2. **成本考虑**: 免费版 ES 已能满足需求
3. **灵活性**: 手动融合可以灵活调整权重

**权衡**：
- ✅ 免费、灵活
- ⚠️ 需要两次查询（性能略低）
- ⚠️ 代码复杂度稍高

### 7.2 为什么向量维度选择 1024？

**原因**：
1. **模型默认**: `text-embedding-v4` 默认输出 1024 维
2. **性能平衡**: 
   - 维度太低（如 256）：语义表达能力不足
   - 维度太高（如 2048）：存储和计算成本增加
   - 1024 是性能和效果的平衡点

### 7.3 为什么权重设置为 0.6:0.4？

**原因**：
1. **语义优先**: 向量检索能理解语义，更适合小说搜索场景
2. **保留精确匹配**: 文本检索保留关键词精确匹配能力
3. **可调参数**: 可根据实际效果调整

**调优建议**：
- 如果用户更关注"精确匹配书名"，可调整为 `0.4:0.6`
- 如果用户更关注"语义理解"，可调整为 `0.7:0.3`

## 8. 技术债务与未来优化

### 8.1 当前限制

1. **向量生成延迟**: 每次搜索都需要调用 DashScope API（~200ms）
2. **两次查询**: KNN + BM25 需要两次 ES 查询
3. **内存合并**: 结果合并需要在内存中完成（大数据量时可能成为瓶颈）

### 8.2 未来优化方向

1. **向量缓存**: 缓存常用查询词的向量
2. **异步向量生成**: 书籍更新时异步生成向量，搜索时直接使用
3. **ES 升级**: 如果升级到商业版，可使用原生 RRF
4. **本地模型**: 考虑使用本地 Embedding 模型（如 BGE-M3），减少 API 调用

## 9. 参考资料

- [Elasticsearch Dense Vector 文档](https://www.elastic.co/guide/en/elasticsearch/reference/current/dense-vector.html)
- [DashScope Embedding 文档](https://help.aliyun.com/zh/dashscope/developer-reference/text-embedding-api-details)
- [BM25 算法原理](https://en.wikipedia.org/wiki/Okapi_BM25)
- [HNSW 算法论文](https://arxiv.org/abs/1603.09320)

