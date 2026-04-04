# 书籍服务模块详解（面试版）

本文档聚焦 `novel-book-service`，覆盖书籍与章节核心业务、审核流、搜索同步与消息通知。

## 1. 模块定位

`novel-book` 是小说平台的核心业务域，负责：

- 书籍基础信息管理（增删改查、分类、榜单）
- 章节内容管理（发布、更新、删除、阅读）
- 审核流程承接（AI审核 + 人工审核）
- 数据对外同步（搜索索引、消息通知）

一句话概括：`novel-book` 是“内容生产与内容状态流转的事实源（Source of Truth）”。

## 2. 边界与协作关系

## 2.1 上游

- `novel-user`：作者侧请求入口，发 MQ 提交书籍/章节操作
- `novel-gateway`：前台读者查询入口转发

## 2.2 下游/协作

- `novel-ai`：接收审核请求并回传审核结果
- `novel-search`：消费书籍变更消息，同步 ES
- `novel-user`：接收审核通过通知、SSE 推送
- `Redis`：缓存书籍信息、章节目录、榜单
- `RocketMQ`：异步主干（提交、审核、变更、通知）

## 3. 设计总览

## 3.1 写链路：异步提交 + 消费落库

作者新增/更新书籍、章节并不直接在入口线程做复杂操作，而是：

1. 上游发 `BookSubmitMqDto` / `ChapterSubmitMqDto`
2. 书籍服务 MQ 消费者执行权限校验与落库
3. 按需触发审核、缓存更新、ES 同步、消息通知

优势：

- 降低入口 RT，减轻上游服务压力
- 将复杂状态流转集中到单点处理

## 3.2 审核流：AI优先 + 低置信度转人工

`BookAuditServiceImpl` 的核心策略：

- AI审核通过且置信度高：直接通过
- AI审核通过但置信度低：标记待人工审核
- AI审核不通过：直接驳回并记录原因

并且对书籍和章节都维护 `content_audit` 审核记录，支持后续人工复核。

## 3.3 同步流：通过消息驱动外部一致

审核通过或内容变更时，会发送书籍变更 MQ：

- 搜索服务据此更新 ES 索引
- 用户服务可接收通知并推送给作者

这是一种“主库先行、外部系统最终一致”的典型模式。

> 说明：并非所有消息都严格在事务提交后发送。当前代码中“事务后发送”和“直接发送”并存，因此更依赖幂等与补偿机制保障一致性。

## 4. 关键实现拆解

## 4.1 读者侧接口入口

`FrontBookController` 负责前台读场景：

- 分类、榜单、推荐
- 章节目录、正文内容、上一章/下一章
- 评论分页、首页书单、书籍详情

特点：

- 读路径集中在 `BookSearchService` + `BookReadService`
- 高并发查询场景通过缓存与预聚合任务优化（如点击榜刷新任务）

## 4.2 作者侧核心服务

`BookAuthorServiceImpl` 核心职责：

- 接收新增/更新书籍与章节请求
- 执行基础同步校验（如书名重复、作者权限）
- 发送提交消息到 MQ
- 删除书籍/章节时执行事务落库并发送变更消息（该类操作仍是同步请求）

关键点：

- 删除章节时会重算 `wordCount` 和 `lastChapter*`
- 删除书籍会级联删除章节
- 事务提交后再发变更消息，减少脏消息风险

## 4.3 书籍提交消费者

`BookSubmitListener` 处理 `BookSubmitMq`：

- `ADD`：
  - 再次校验书名
  - 插入 `book_info`
  - 根据开关决定初始审核状态
  - 可触发 AI 审核请求
- `UPDATE`：
  - 权限校验
  - 按字段增量更新
  - 书名/简介变化时重置待审核
  - 清理缓存
  - 按需触发重新审核

## 4.4 章节提交消费者

`ChapterSubmitListener` 处理 `ChapterSubmitMq`：

- 双重权限校验（防越权）
- 区分 `CREATE` / `UPDATE`
- 维护章节字数与书籍总字数
- 重算最新章节（只基于审核通过章节）
- 章节审核开关开启时发送审核请求
- 发送章节更新通知消息

## 4.5 审核结果消费者与处理器

- `BookAuditResultListener`：接收书籍审核结果消息
- `ChapterAuditResultListener`：接收章节审核结果消息
- 两者最终都委托 `BookAuditServiceImpl`

`BookAuditServiceImpl` 负责：

- 更新审核记录（`content_audit`）
- 更新业务表审核状态（`book_info` / `book_chapter`）
- 审核通过时：
  - 更新书籍最新章节信息（章节场景）
  - 清缓存（书籍缓存/章节目录缓存）
  - 发 ES 变更消息
  - 给作者发站内消息 + SSE 推送

## 4.6 关键 MQ 主题映射（建议面试直接说）

- 书籍提交：`BookSubmitMq.TOPIC` + `TAG_SUBMIT`
- 章节提交：`ChapterSubmitMq.TOPIC` + `TAG_SUBMIT`
- 书籍审核请求：`BookAuditRequestMq.TOPIC` + `TAG_AUDIT_BOOK_REQUEST`
- 章节审核请求：`BookAuditRequestMq.TOPIC` + `TAG_AUDIT_CHAPTER_REQUEST`
- 书籍审核结果：`BookAuditResultMq.TOPIC` + `TAG_AUDIT_BOOK_RESULT`
- 章节审核结果：`BookAuditResultMq.TOPIC` + `TAG_AUDIT_CHAPTER_RESULT`
- 书籍变更（ES同步）：`BookChangeMq.TOPIC` + `TAG_UPDATE`
- 章节更新通知：`BookChangeMq.TOPIC` + `TAG_CHAPTER_UPDATE`

## 5. 数据一致性与幂等策略

## 5.1 双重校验

- 上游服务已做一次权限校验
- 消费者再做一次权限校验，防伪造消息与越权

## 5.2 事务与消息顺序

- 删除等关键写操作用事务
- 尽量在事务提交后发送消息（部分场景通过事务同步回调）
- 消费者内部大量“捕获异常不抛出”策略，避免重复消费但增加了静默失败风险

## 5.3 失败容忍

- 多处消息通知失败不会回滚主业务，保障主链路可用
- 通过日志 + 后续补偿任务兜底（当前主要靠日志排查）

## 6. 核心业务时序（可背诵）

## 6.1 新增书籍

1. `novel-user` 发送 `BookSubmitMq(ADD)`
2. `BookSubmitListener` 消费并插入 `book_info`
3. 开启审核时发送 `BookAuditRequestMq`
4. AI 返回结果后由 `BookAuditServiceImpl` 更新审核状态
5. 审核通过后发送 `BookChangeMq` 触发搜索同步

## 6.2 更新章节

1. `novel-user` 发送 `ChapterSubmitMq(UPDATE)`
2. `ChapterSubmitListener` 更新章节与字数
3. 开启审核则置待审核并发审核请求
4. 审核结果回传后更新章节状态
5. 若通过则更新书籍最新章节、清目录缓存并发 ES 变更

## 6.3 人工审核

1. 管理端发起人工审核
2. `BookAuditServiceImpl.manualAudit` 更新审核记录
3. 同步更新业务表状态
4. 通过时执行 ES 同步与作者通知

## 7. 典型排障手册

## 7.1 症状：作者提交成功但看不到数据

排查路径：

1. `BookSubmitMq` / `ChapterSubmitMq` 是否发送成功
2. 对应消费者是否消费
3. 审核状态是否仍为待审核/驳回
4. 是否因权限校验失败被消费者忽略
5. 是否因 AI 审核低置信度进入人工审核等待

## 7.2 症状：章节已通过审核但前台目录未更新

排查路径：

1. `processChapterAuditResult` 是否执行到更新章节状态
2. 是否成功调用更新 `book_info.lastChapter*`
3. 章节目录缓存是否已删除
4. 前台是否命中旧缓存或读副本延迟

## 7.3 症状：搜索结果未同步

排查路径：

1. 审核通过后是否发出 `BookChangeMq`
2. `novel-search` 消费是否正常
3. ES 写入是否成功
4. 审核状态是否本身未通过

## 7.4 症状：作者没收到审核通过通知

排查路径：

1. 审核通过分支是否触发 `sendAuditPassMessageToAuthor`
2. 用户服务消息接口是否成功
3. SSE 推送失败是否仅告警未重试

## 8. 当前优势与技术债

## 8.1 优势

- 内容主域集中，状态流转清晰
- 审核链路完整（AI + 人工）
- 与搜索、消息系统解耦
- 前台查询与后台写入职责分离明显

## 8.2 技术债

- 部分消费者 `catch` 后不抛异常，重试与补偿依赖人工观察
- 审核与通知流程跨多个系统，缺少统一任务状态中心
- 缓存失效策略分散，长期可能出现维护成本上升
- 同一业务存在“同步删除 + 异步提交”两种写路径，文档和实现需持续对齐

## 9. 可落地优化建议

1. 建立“审核流程状态表”，记录每一步成功/失败，便于自动补偿  
2. 为关键 MQ 加入死信队列（DLQ）与告警，减少静默失败  
3. 对通知链路（站内消息 + SSE）增加重试与幂等标记  
4. 增加审核与提交链路监控指标（成功率、平均耗时、失败原因分布）  
5. 把书籍/章节状态流转抽象为状态机，减少条件分支复杂度  

## 10. 面试高频追问（建议准备）

- 为什么内容写入要异步，不怕一致性问题吗？
- AI 审核低置信度为什么转人工，而不是直接通过或拒绝？
- 审核通过后为什么要清缓存再发 ES 消息？
- 章节更新如何保证 `wordCount` 和最新章节信息正确？
- 消费者不抛异常会不会丢消息，怎么补偿？

## 11. 关键代码索引

- 前台入口：`novel-book/novel-book-service/src/main/java/com/novel/book/controller/front/FrontBookController.java`
- 作者侧服务：`novel-book/novel-book-service/src/main/java/com/novel/book/service/impl/BookAuthorServiceImpl.java`
- 书籍提交消费者：`novel-book/novel-book-service/src/main/java/com/novel/book/mq/BookSubmitListener.java`
- 章节提交消费者：`novel-book/novel-book-service/src/main/java/com/novel/book/mq/ChapterSubmitListener.java`
- 书籍审核结果消费者：`novel-book/novel-book-service/src/main/java/com/novel/book/mq/BookAuditResultListener.java`
- 章节审核结果消费者：`novel-book/novel-book-service/src/main/java/com/novel/book/mq/ChapterAuditResultListener.java`
- 审核核心服务：`novel-book/novel-book-service/src/main/java/com/novel/book/service/impl/BookAuditServiceImpl.java`
