# 用户服务模块详解（面试版）

本文档聚焦 `novel-user-service`，用于面试讲解与后续维护复盘。

## 1. 模块定位

`novel-user` 在系统中承担“用户域 + 作者域入口 + AI积分结算 + 消息中心协同”四类职责：

- 用户注册、登录、登出、个人资料管理
- 作者身份注册与作者侧操作入口（发布书籍/章节）
- AI能力消费的积分扣减与失败回滚
- 消息、SSE 通知、书架等用户体验相关能力

一句话概括：`novel-user` 是“用户身份与创作入口编排服务”，核心思路是“入口同步校验 + 下游异步处理”。

## 2. 模块边界与上下游

## 2.1 上游

- `novel-gateway`：所有前台用户/作者请求都经由网关转发到用户服务。

## 2.2 下游

- `novel-book`（Feign + MQ）：写操作以 MQ 为主，部分查询/删除走 Feign
- `novel-ai`（Feign）：AI审核、润色、封面生成能力
- `Redis`：缓存、登录防刷、token黑名单、积分实时值
- `RocketMQ`：异步积分消费持久化、积分回滚持久化

## 2.3 关键依赖（来自 `pom.xml`）

- `novel-book-api`、`novel-ai-api`、`novel-user-api`
- `rocketmq-spring-boot-starter`
- `alipay-sdk-java`（支付相关扩展能力）

## 3. 核心设计思想

## 3.1 认证前置 + 线程上下文透传

通过 `AuthInterceptor` 在请求进入 Controller 前完成：

1. 读取 JWT
2. 校验黑名单 token
3. 解析用户 ID
4. 从缓存获取用户/作者信息
5. 将 `userId`、`authorId`、`authorPenName` 写入 `UserHolder`

请求结束后在 `afterCompletion` 里清理 `UserHolder`，避免线程复用污染。

## 3.2 入口快速返回 + MQ 异步业务落地

作者发布/更新书籍、章节时，用户服务不直接做重 DB 事务，而是：

- 构建 `BookSubmitMqDto` / `ChapterSubmitMqDto`
- 发送到 RocketMQ
- 快速返回前端

优势是显著降低接口 RT，减少网关和用户服务线程占用。

> 说明：该策略主要覆盖作者侧写链路（发布/更新）。查询类接口和部分删除类接口仍可能走同步 Feign。

## 3.3 积分先扣后服务 + 失败回滚

AI审核/润色/封面生成链路遵循：

1. 先扣积分（Redis 作为实时积分来源）
2. 调 AI 服务
3. 调用失败则回滚积分
4. 扣减和回滚事件都异步持久化到数据库（MQ消费者）

这个模式在体验和一致性之间做了平衡：先保证请求可响应，再通过异步落库做最终一致。

## 4. 关键实现拆解

## 4.1 Web 层入口

`AuthorController` 是作者端核心入口，覆盖：

- 作者状态与注册
- 书籍/章节新增、更新、删除
- 作者消息中心
- AI审核/润色/封面能力

其中 `publishBook` / `updateBook` / `publishBookChapter` / `updateBookChapter` 均改为发送 MQ。

## 4.2 认证与鉴权

- `WebConfig`：拦截 `/api/front/user/**` 和 `/api/author/**`
- 放行注册登录接口
- `AuthInterceptor`：
  - JWT 解析失败 -> 登录失效
  - token 在黑名单 -> 登录失效
  - 用户不存在/状态异常 -> 拒绝访问
  - 读缓存填充 `UserHolder`

## 4.3 用户账号安全

`UserServiceImpl` 关键策略：

- 密码支持 MD5 旧格式与 BCrypt 新格式并行
- 老密码登录成功后自动升级为 BCrypt
- 登录失败计数 + 锁定（Redis）
- 登出时将 token 加入黑名单（MD5 后写 Redis，降低内存占用）

## 4.4 缓存策略

`CacheServiceImpl` 中：

- 用户缓存：`USER_INFO_CACHE_NAME`
- 作者缓存：`AUTHOR_INFO_CACHE_NAME`
- 作者缓存支持双 key（`userId:*`、`authorId:*`），提升查找灵活性
- 用户信息修改后主动清缓存，保障读一致性

## 4.5 作者侧编排服务

`AuthorServiceImpl` 的职责不是重业务落库，而是“编排 + 转发”：

- 组装 MQ DTO 发往 `novel-book`
- 透传 Feign 查询与部分删除操作（作者书籍列表、章节列表、章节详情、删除）
- 接入消息服务（作者消息读写）
- 统一处理 AI 积分扣减和失败回滚

## 4.7 关键 MQ 主题映射（建议面试直接说）

- 书籍提交：`BookSubmitMq.TOPIC` + `TAG_SUBMIT`
- 章节提交：`ChapterSubmitMq.TOPIC` + `TAG_SUBMIT`
- 积分扣减持久化：`AuthorPointsConsumeMq.TOPIC` + `TAG_DEDUCT`
- 积分回滚持久化：`AuthorPointsConsumeMq.TOPIC` + `TAG_ROLLBACK`

## 4.6 积分消费与回滚（MQ）

- `AuthorPointsConsumeListener`：消费扣减消息，同步 Redis 积分到 DB，并写消费日志
- `AuthorPointsRollbackListener`：消费回滚消息，同步 Redis 积分到 DB，并写回滚日志
- 幂等：日志写入使用 `idempotentKey`，重复消费可拦截

## 5. 关键业务时序（可背诵）

## 5.1 作者发布书籍

1. 请求到 `AuthorController.publishBook`
2. 从 `UserHolder` 取 `authorId`/`penName`
3. `AuthorServiceImpl` 组装 `BookSubmitMqDto`
4. 发送 RocketMQ（`BookSubmitMq`）
5. 立即返回成功
6. `novel-book` 消费消息后落库/审核

## 5.2 AI审核（积分版）

1. `AuthorController.audit` 入参校验
2. `AuthorServiceImpl.audit` 先扣积分
3. 调用 AI 服务审核接口
4. 成功则直接返回审核结果
5. 失败则执行积分回滚并返回失败提示
6. 扣减/回滚事件异步持久化

## 5.3 登录安全链路

1. 用户登录 -> 账号锁定状态检查
2. 校验密码（兼容 MD5 + BCrypt）
3. 失败次数累计，超阈值锁定
4. 成功后清失败计数并签发 JWT
5. 登出时 token 加入黑名单

## 6. 数据一致性设计

- **用户会话一致性**：JWT + 黑名单双重控制
- **缓存一致性**：修改后清缓存，不做强一致读写锁
- **积分一致性**：Redis 实时值 + MQ 落库最终一致
- **跨服务一致性**：写链路以消息驱动为主，查询/部分删除走同步调用；关键操作由下游再次权限校验

## 7. 常见故障与排查

## 7.1 症状：作者接口返回未认证

优先排查：

1. Header 是否携带 token
2. token 是否过期/黑名单命中
3. `AuthInterceptor` 是否成功写入 `UserHolder.authorId`
4. 作者信息缓存是否过期或异常

## 7.2 症状：发布成功但书籍未出现

排查顺序：

1. `AuthorServiceImpl` MQ 发送日志
2. RocketMQ 是否堆积
3. `novel-book` 消费日志是否异常
4. 书籍审核状态是否为通过
5. 前台查询接口是否只返回审核通过数据

## 7.3 症状：AI调用失败但积分未退回

排查顺序：

1. AI 调用异常日志是否触发回滚逻辑
2. 回滚 MQ 是否发送成功
3. 回滚消费者是否消费成功
4. 幂等 key 是否冲突导致日志插入被拦截

## 8. 当前优点与风险

## 8.1 优点

- 入口职责清晰：校验、编排、异步投递
- 接口 RT 友好：核心写链路异步化
- 认证链完整：JWT + 黑名单 + 用户状态检查
- 积分链可补偿：失败回滚 + 消费日志沉淀

## 8.2 风险点

- `UserHolder` 基于线程上下文，异步场景不能直接复用
- 积分采用 Redis 作为实时来源，需关注 Redis 可用性
- 多处 try-catch 不抛异常，可能隐藏局部失败（依赖日志告警）
- token 黑名单 TTL 当前固定 7 天，和 token 实际剩余有效期可能存在偏差

## 9. 可落地优化建议

1. 将积分扣减/回滚升级为“事务消息 + 状态机”，减少人工补偿成本  
2. 增加统一 traceId 贯穿用户服务 -> MQ -> 下游消费者  
3. 对 `AuthorController` 的高频接口增加参数与频率防刷  
4. 细化 token 黑名单过期策略，按 token 实际剩余 TTL 设置过期  
5. 输出用户服务指标看板：登录失败率、黑名单命中率、MQ发送失败率  

## 10. 面试高频追问（建议准备）

- 为什么选择“入口异步化”，而不是全部同步强一致？
- token 黑名单为什么用 MD5 后再存 Redis？
- 如何保证积分扣减与 AI 调用失败回滚的一致性？
- 缓存击穿/穿透/雪崩在这个模块怎么防？
- 如果 MQ 消费失败或重复消费，如何保证幂等？

## 11. 关键代码索引

- 认证拦截：`novel-user/novel-user-service/src/main/java/com/novel/user/config/AuthInterceptor.java`
- 拦截注册：`novel-user/novel-user-service/src/main/java/com/novel/user/config/WebConfig.java`
- 作者入口：`novel-user/novel-user-service/src/main/java/com/novel/user/controller/front/AuthorController.java`
- 作者编排：`novel-user/novel-user-service/src/main/java/com/novel/user/service/impl/AuthorServiceImpl.java`
- 用户核心：`novel-user/novel-user-service/src/main/java/com/novel/user/service/impl/UserServiceImpl.java`
- 缓存服务：`novel-user/novel-user-service/src/main/java/com/novel/user/service/impl/CacheServiceImpl.java`
- token 黑名单：`novel-user/novel-user-service/src/main/java/com/novel/user/service/TokenBlacklistService.java`
- 积分扣减消费者：`novel-user/novel-user-service/src/main/java/com/novel/user/mq/AuthorPointsConsumeListener.java`
- 积分回滚消费者：`novel-user/novel-user-service/src/main/java/com/novel/user/mq/AuthorPointsRollbackListener.java`
