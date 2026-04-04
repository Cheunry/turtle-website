# 搜索服务模块详解（面试版）

本文档聚焦 `novel-search` 模块，强调“可讲清设计 + 可落地排障 + 可解释取舍”。

## 1. 模块定位

`novel-search` 是读侧检索引擎，目标是同时满足：

- **精准匹配**：用户搜书名、作者名时尽量命中
- **语义召回**：用户搜剧情、题材、近义表达时也能命中
- **读写解耦**：主业务写库后通过 MQ 异步同步到 ES

一句话概括：这是一个“BM25 + 向量检索”的混合搜索服务，数据通过消息驱动最终一致。

## 2. 技术栈与依赖

来自 `novel-search/pom.xml` 的关键依赖：

- `elasticsearch-java`（ES 客户端）
- `spring-ai-alibaba-starter-dashscope`（向量模型）
- `rocketmq-spring-boot-starter`（变更消息消费）
- `xxl-job-core`（全量同步任务支持）
- `novel-book-api`（通过 Feign 获取书籍数据）

## 3. 核心组件

## 3.1 HTTP 入口

`SearchController` 提供三个主要接口：

- `GET /api/front/search/books`：搜索主接口
- `GET /api/front/search/test`：服务健康测试
- `GET /api/front/search/sync/all`：手动触发全量同步（异步触发）

## 3.2 搜索执行器

`SearchServiceImpl` 负责：

- 构建 ES BoolQuery 过滤条件
- 判断是否使用 Hybrid Search
- 执行 KNN 与 BM25 查询
- 合并两路结果并分页返回
- 混合检索失败时降级为纯文本搜索

## 3.3 实时同步消费者

`BookChangeMqListener` 消费 `BookChangeMq`，按 `bookId` 同步 ES：

1. 解析消息体（支持单 id 或 id 列表）
2. 通过 Feign 拉取最新书籍数据
3. 生成 embedding（失败可降级）
4. upsert 到 ES
5. 如数据不存在且 ES 有旧文档，则执行删除

## 3.4 全量同步任务

`AllBookToEsTask` 用于全量重建索引：

- 按 `maxId` 分批拉取书籍数据
- 批量生成向量并 Bulk 写入 ES
- 适用于首次上线、索引重建、异常修复

## 4. 搜索模式设计

## 4.1 模式判定

`searchMode` 的行为（基于 `SearchServiceImpl`）：

- `1`：强制混合搜索（语义 + 关键词）
- `2`：强制文本搜索（书名优先）
- `0` 或空：智能模式（关键词长于 6 或包含空格时倾向混合）

## 4.2 文本检索策略（BM25）

- 多字段匹配：`bookName^2`、`authorName^1.8`、`bookDesc^0.1`
- 高亮字段：书名、作者名
- 支持筛选：方向、分类、状态、字数区间、更新时间等

当 `searchMode=2` 时还会使用：

- `bookName` 的 match + `bookName.keyword` 的 wildcard 组合
- 用于改善中英混合或特殊分词情况下的召回

## 4.3 向量检索策略（KNN）

- 查询词实时 embedding（DashScope）
- `k = recallSize`
- `numCandidates = max(recallSize * 2, 200)`
- 在 knn 查询侧下推 filter 条件，避免召回后再过滤

## 4.4 结果融合策略

当前没有使用商业版 RRF，而是手动加权融合：

1. KNN 与 BM25 分别召回
2. 各自按排名倒数打分：`1/(rank+1)`
3. 综合分：`knn*0.6 + bm25*0.4`
4. 去重、排序、分页

优势：免费、易调参；代价：需要自己维护融合逻辑与评估指标。

## 5. 数据同步与一致性

## 5.1 写链路一致性模型

- `novel-book` 是主数据源
- `novel-search` 通过 MQ 异步同步 ES
- 属于“最终一致”，不是强一致

## 5.2 失败处理策略

- 向量生成失败：保留文本字段入 ES（可文本检索）
- 删除 ES 文档失败：大多记录告警，不阻断主流程
- 单条同步异常：抛异常让 MQ 重试（注意批次副作用）

## 5.3 关键取舍

- 可用性优先于绝对强一致
- 优先保证“搜得到”（文本兜底），再追求“搜得准”（向量增强）

## 6. 典型排障手册

## 6.1 症状：新书审核通过但搜索不到

排查顺序：

1. `novel-book` 是否发送 `BookChangeMq`
2. `BookChangeMqListener` 是否成功消费
3. Feign 拉取书籍数据是否返回空
4. ES 中文档是否存在
5. 书籍 `auditStatus` 是否满足可索引条件

## 6.2 症状：搜索可用但语义效果变差

排查顺序：

1. embedding API 是否频繁失败（是否触发文本降级）
2. Nacos 中向量维度配置是否和 ES `dims` 一致
3. 融合权重是否被改动
4. 查询是否大量落入 `searchMode=2`（仅文本）

## 6.3 症状：接口响应变慢

排查顺序：

1. 向量生成耗时（外部 API）
2. ES 查询耗时（KNN/BM25 哪条慢）
3. 召回量是否过大（`recallSize`）
4. 是否存在慢日志与热词突增

## 6.4 症状：全量同步很慢

排查顺序：

1. 每批数据量是否合理
2. embedding API 限流与网络延迟
3. ES Bulk 错误明细
4. 是否需要分时段或并发分片同步

## 7. 当前优点与风险

## 7.1 优点

- 混合检索效果明显优于纯关键词
- 文本兜底策略保障可用性
- MQ 驱动解耦写入与索引
- 支持实时同步 + 全量修复双路径

## 7.2 风险

- 查询实时 embedding 带来外部依赖延迟与成本
- 手动融合逻辑长期维护成本高
- 消息重试可能导致批次重复处理，需要关注幂等
- 运维上对 Nacos、MQ、ES 可用性依赖较强

## 8. 可落地优化建议

1. 增加“查询向量缓存”（热门词 TTL 缓存）  
2. 增加“搜索结果缓存”（短 TTL + 热词）  
3. 构建离线评测集，持续优化融合权重  
4. 为 MQ 消费失败引入更清晰的 DLQ 告警链路  
5. 将全量同步任务接入调度中心并增加可观测指标  

## 9. 面试高频追问

- 为什么不用纯 ES 文本检索？
- 为什么不直接用 ES 原生 RRF？
- 向量生成失败时怎么保证服务可用？
- 如何保证主库和 ES 的一致性？
- 如何平衡搜索效果、延迟和成本？

## 10. 关键代码索引

- 启动类：`novel-search/src/main/java/com/novel/search/NovelSearchApplication.java`
- 控制器：`novel-search/src/main/java/com/novel/search/controller/front/SearchController.java`
- 搜索实现：`novel-search/src/main/java/com/novel/search/service/impl/SearchServiceImpl.java`
- 实时同步：`novel-search/src/main/java/com/novel/search/listener/BookChangeMqListener.java`
- 全量同步：`novel-search/src/main/java/com/novel/search/config/AllBookToEsTask.java`
- XXL 配置：`novel-search/src/main/java/com/novel/search/config/XxlJobConfig.java`

## 11. 深度专题文档

更完整的原理与细节见：`doc/es/search_system_complete_guide.md`  
建议把该专题文档作为“深入阅读版”，本文件作为“面试输出版”。
