# AI 封面生图线程池与异步任务设计

## 背景

AI 封面生图是典型的长耗时外部调用：一次任务通常需要等待 DashScope 生成图片，随后还可能把临时图片转存到 COS。若在 HTTP 请求线程内同步等待，会带来几个问题：

- 用户接口长时间阻塞，网关、Feign、Tomcat 线程都被占用。
- 外部模型服务慢或限流时，请求会在应用内堆积。
- 生图能力通常受模型侧 QPS、账号配额、机器资源共同约束，不能无上限并发。
- 失败后还涉及作者积分回滚，必须保证幂等和职责边界清晰。

因此当前实现采用“HTTP 提交任务立即返回 jobId + 专用有界线程池异步执行 + Redis 记录任务状态 + 前端轮询结果”的模式。

## 当前链路总览

```text
前端 BookEdit.vue
  -> POST /author/ai/cover

novel-user
  -> AuthorController.generateCover
  -> AuthorServiceImpl.generateCover
  -> deductPoints(requestId)
  -> 准备 CoverImageAsyncSubmitReqDto
  -> Feign 调 novel-ai /inner/ai/generate/image/async

novel-ai
  -> InnerAiController.generateImageAsync
  -> ImageAsyncGenerationService.submit
  -> 检查生图线程池容量
  -> Redis 创建 QUEUED 任务
  -> 提交到 imageGenerationExecutor
  -> 立即返回 jobId

前端
  -> GET /author/ai/cover/jobs/{jobId}
  -> 轮询 Redis 中的任务状态

novel-ai 工作线程
  -> ImageServiceImpl.generateImage(prompt, jobId)
  -> 调 DashScope ImageModel
  -> 成功后可转存 COS
  -> Redis 标记 SUCCEEDED / FAILED
  -> 失败时通知 novel-user 做积分补偿
```

涉及的核心代码：

| 模块 | 代码 | 作用 |
|---|---|---|
| 前端 | [BookEdit.vue](/Users/cheunry/Project/Java/turtle-website-front/src/views/author/BookEdit.vue) | 发起 AI 封面生图、轮询 job 状态、展示预览图 |
| user-api | [AuthorPointsConsumeReqDto.java](/Users/cheunry/Project/Java/turtle-website/novel-user/novel-user-api/src/main/java/com/novel/user/dto/req/AuthorPointsConsumeReqDto.java) | 作者积分扣减请求，包含 requestId、封面 prompt 等字段 |
| user-api | [CoverGenerationFailedReqDto.java](/Users/cheunry/Project/Java/turtle-website/novel-user/novel-user-api/src/main/java/com/novel/user/dto/req/CoverGenerationFailedReqDto.java) | AI 生图失败回调 DTO |
| user-api | [AuthorFeign.java](/Users/cheunry/Project/Java/turtle-website/novel-user/novel-user-api/src/main/java/com/novel/user/feign/AuthorFeign.java) | AI 服务回调 user 服务的 Feign 客户端 |
| user-service | [AuthorController.java](/Users/cheunry/Project/Java/turtle-website/novel-user/novel-user-service/src/main/java/com/novel/user/controller/front/AuthorController.java) | 作者端 `POST /author/ai/cover` 和 job 查询入口 |
| user-service | [InnerAuthorController.java](/Users/cheunry/Project/Java/turtle-website/novel-user/novel-user-service/src/main/java/com/novel/user/controller/inner/InnerAuthorController.java) | 内部积分扣减、回滚、生图失败回调入口 |
| user-service | [AuthorService.java](/Users/cheunry/Project/Java/turtle-website/novel-user/novel-user-service/src/main/java/com/novel/user/service/AuthorService.java) | 作者服务接口 |
| user-service | [AuthorServiceImpl.java](/Users/cheunry/Project/Java/turtle-website/novel-user/novel-user-service/src/main/java/com/novel/user/service/impl/AuthorServiceImpl.java) | 扣积分、提交生图任务、提交失败回滚、失败回调补偿 |
| user-service | [AuthorPointsTx.java](/Users/cheunry/Project/Java/turtle-website/novel-user/novel-user-service/src/main/java/com/novel/user/dao/entity/AuthorPointsTx.java) | 积分事务表实体，支撑扣减和回滚幂等 |
| ai-api | [AiFeign.java](/Users/cheunry/Project/Java/turtle-website/novel-ai/novel-ai-api/src/main/java/com/novel/ai/feign/AiFeign.java) | user 服务调用 AI 服务的 Feign 客户端 |
| ai-api | [CoverImageAsyncSubmitReqDto.java](/Users/cheunry/Project/Java/turtle-website/novel-ai/novel-ai-api/src/main/java/com/novel/ai/dto/req/CoverImageAsyncSubmitReqDto.java) | 异步生图提交 DTO |
| ai-api | [ImageGenJobSubmitRespDto.java](/Users/cheunry/Project/Java/turtle-website/novel-ai/novel-ai-api/src/main/java/com/novel/ai/dto/resp/ImageGenJobSubmitRespDto.java) | 生图任务提交返回 jobId |
| ai-api | [ImageGenJobStatusRespDto.java](/Users/cheunry/Project/Java/turtle-website/novel-ai/novel-ai-api/src/main/java/com/novel/ai/dto/resp/ImageGenJobStatusRespDto.java) | 生图任务状态查询返回 |
| ai-service | [InnerAiController.java](/Users/cheunry/Project/Java/turtle-website/novel-ai/novel-ai-service/src/main/java/com/novel/ai/controller/inner/InnerAiController.java) | 内部异步生图提交和任务查询接口 |
| ai-service | [FrontAiController.java](/Users/cheunry/Project/Java/turtle-website/novel-ai/novel-ai-service/src/main/java/com/novel/ai/controller/front/FrontAiController.java) | 前台 AI 接口，保留直接生图/查询入口 |
| ai-service | [ImageGenerationGate.java](/Users/cheunry/Project/Java/turtle-website/novel-ai/novel-ai-service/src/main/java/com/novel/ai/service/ImageGenerationGate.java) | 生图提交网关，统一进入异步任务 |
| ai-service | [ImageGenerationExecutorConfiguration.java](/Users/cheunry/Project/Java/turtle-website/novel-ai/novel-ai-service/src/main/java/com/novel/ai/config/ImageGenerationExecutorConfiguration.java) | 生图专用线程池和线程池 Gauge 指标 |
| ai-service | [ImageGenerationExecutorProperties.java](/Users/cheunry/Project/Java/turtle-website/novel-ai/novel-ai-service/src/main/java/com/novel/ai/config/ImageGenerationExecutorProperties.java) | 生图线程池配置属性 |
| ai-service | [ImageAsyncGenerationService.java](/Users/cheunry/Project/Java/turtle-website/novel-ai/novel-ai-service/src/main/java/com/novel/ai/image/job/ImageAsyncGenerationService.java) | 异步任务提交、容量检查、执行、失败通知、任务指标 |
| ai-service | [ImageJobRedisStore.java](/Users/cheunry/Project/Java/turtle-website/novel-ai/novel-ai-service/src/main/java/com/novel/ai/image/job/ImageJobRedisStore.java) | Redis job 状态读写 |
| ai-service | [ImageGenJobStatus.java](/Users/cheunry/Project/Java/turtle-website/novel-ai/novel-ai-service/src/main/java/com/novel/ai/image/job/ImageGenJobStatus.java) | 生图任务状态枚举 |
| ai-service | [ImageService.java](/Users/cheunry/Project/Java/turtle-website/novel-ai/novel-ai-service/src/main/java/com/novel/ai/service/ImageService.java) | 生图服务接口 |
| ai-service | [ImageServiceImpl.java](/Users/cheunry/Project/Java/turtle-website/novel-ai/novel-ai-service/src/main/java/com/novel/ai/service/impl/ImageServiceImpl.java) | 调 DashScope 生图、转存 COS、重试分类 |
| ai-service | [ImageGenerationException.java](/Users/cheunry/Project/Java/turtle-website/novel-ai/novel-ai-service/src/main/java/com/novel/ai/image/exception/ImageGenerationException.java) | 生图异常基类和失败类型 |
| ai-service | [ImageGenerationTransientException.java](/Users/cheunry/Project/Java/turtle-website/novel-ai/novel-ai-service/src/main/java/com/novel/ai/image/exception/ImageGenerationTransientException.java) | 可重试的瞬时异常 |
| ai-service | [ImageGenerationExceptionClassifier.java](/Users/cheunry/Project/Java/turtle-website/novel-ai/novel-ai-service/src/main/java/com/novel/ai/image/exception/ImageGenerationExceptionClassifier.java) | 将底层异常分类为瞬时/参数/鉴权配额/内容安全/未知 |
| Nacos local | [novel-ai-service-dev.yml](/Users/cheunry/Project/Java/turtle-website/nacos/local/DEFAULT_GROUP/novel-ai-service-dev.yml) | 本地 AI 服务端口、限流、线程池等配置 |
| Nacos prod | [novel-ai-service-prod.yml](/Users/cheunry/Project/Java/turtle-website/nacos/prod/DEFAULT_GROUP/novel-ai-service-prod.yml) | 生产 AI 服务配置 |
| Nacos common | [novel-common-base.yml](/Users/cheunry/Project/Java/turtle-website/nacos/local/DEFAULT_GROUP/novel-common-base.yml) | Actuator 暴露、Feign 超时等通用配置 |

## 为什么使用有界线程池

生图任务之前讨论过“虚拟线程 + Semaphore”的方案。这个模型适合大量阻塞 I/O 且需要单独限制进入临界区的场景，但对当前生图任务来说，有界线程池更直接：

- 线程数限制“同时真实执行的生图任务数”。
- 队列容量限制“最多允许多少任务等待”。
- 队列满时可以快速拒绝，给用户明确的“当前任务较多”反馈。
- 不会产生大量虚拟线程阻塞在 `Semaphore.acquire()` 上，避免任务上下文无限堆积。

当前线程池是 `ThreadPoolTaskExecutor`，底层是 `ThreadPoolExecutor`。

```java
corePoolSize = 3
maxPoolSize = 3
queueCapacity = 20
RejectedExecutionHandler = AbortPolicy
```

含义是：

- 最多 3 个生图任务并发执行。
- 额外最多 20 个任务排队等待。
- 当 3 个线程都忙且队列满时，新的任务直接拒绝。

配置类为 `ImageGenerationExecutorConfiguration`，配置属性前缀为：

```text
novel.ai.image-gen
```

默认值位于 `ImageGenerationExecutorProperties`：

```java
private int corePoolSize = 3;
private int maxPoolSize = 3;
private int queueCapacity = 20;
private int keepAliveSeconds = 60;
```

## 提交任务时的容量控制

当前提交逻辑不是先无脑创建 Redis 任务，而是先判断线程池是否已经饱和：

```java
activeCount >= maximumPoolSize
&& queue.remainingCapacity() <= 0
```

如果满足，说明所有工作线程都在执行，队列也没有剩余容量。此时直接返回：

```text
AI_IMAGE_GENERATION_BUSY
```

不会生成 `jobId`，也不会创建 Redis 任务。

这避免了一个旧问题：线程池已经满了，但系统先在 Redis 里创建一个用户拿不到的失败任务。

同时，代码仍然保留 `RejectedExecutionException` 兜底：

```text
容量预检查通过
  -> 创建 Redis QUEUED 任务
  -> submit 到线程池
  -> 如果在并发竞态下仍被拒绝
       -> 删除刚创建的 Redis job
       -> 返回 AI_IMAGE_GENERATION_BUSY
```

也就是说，正常饱和时不写 Redis；极小竞态下写了也会清理。

## Redis 任务状态设计

Redis 使用 Hash 存储任务状态，key 前缀为：

```text
novel:ai:image:job:{jobId}
```

默认 TTL：

```text
24 小时
```

主要字段：

```text
status        QUEUED / GENERATING / UPLOADING / SUCCEEDED / FAILED
message       当前状态文案
imageUrl      成功后的图片 URL
errorMessage  失败时给用户看的错误原因
authorId      作者 ID，用于查询任务时做归属校验
submitJson    原始提交上下文，失败回调 novel-user 时使用
```

状态流转：

```text
QUEUED
  -> GENERATING
  -> UPLOADING
  -> SUCCEEDED

QUEUED / GENERATING / UPLOADING
  -> FAILED
```

`submitJson` 只用于失败通知，不再承载“AI 服务自己回滚积分”的语义。为兼容旧数据，读取时仍兼容旧字段 `rollbackJson`。

## 重试策略

之前 `ImageServiceImpl.generateImage` 使用：

```java
@Retryable(retryFor = Exception.class, maxAttempts = 15)
```

这个范围过大，会导致参数错误、鉴权失败、余额不足、内容安全拦截等不可恢复问题也重试，浪费线程池名额。

现在改成只重试瞬时异常：

```java
@Retryable(
    retryFor = ImageGenerationTransientException.class,
    maxAttempts = 3,
    backoff = @Backoff(delay = 3000, multiplier = 1.5, maxDelay = 10000)
)
```

异常分类由 `ImageGenerationExceptionClassifier` 负责。

分类规则：

| 类型 | 是否重试 | 用户可见文案 |
|---|---:|---|
| 瞬时错误：超时、429、502/503/504、网关异常、结果暂不可用 | 是 | AI生图服务繁忙，请稍后重试 |
| 参数错误：prompt 或模型参数非法、分辨率不支持等 | 否 | 封面提示词参数异常，请重新生成提示词后再试 |
| 鉴权、余额、配额：401/403、AccessDenied、InvalidApiKey、QuotaExceeded 等 | 否 | AI生图服务暂不可用，请稍后再试 |
| 内容安全：DataInspectionFailed、inappropriate content、内容安全检查失败等 | 否 | 封面提示词触发内容安全，请调整作品简介或提示词后重试 |
| 其他未知错误 | 否 | 封面生成失败，网站日志已经记录，后续会排查 |

这样可以把线程池资源留给可能恢复的失败，不让必然失败的请求长期占用执行槽。

## 积分扣减与回滚职责

当前职责边界已经收敛：

```text
novel-user：
  扣积分
  回滚积分
  维护 requestId 幂等
  决定一次失败是否需要退积分

novel-ai：
  只负责生图任务
  失败时通知 novel-user：jobId / requestId / authorId / failReason
```

### 提交流程

```text
1. novel-user 先 deductPoints(requestId)
2. novel-user 调 novel-ai submitImageGenerationAsync(requestId, authorId, ...)
3. 如果 submit 直接失败：
   novel-user 自己调用 rollbackPoints(requestId)
4. 如果 submit 成功：
   novel-ai 异步执行生图
```

### 执行失败补偿

```text
1. novel-ai 工作线程执行失败
2. Redis 标记 FAILED
3. novel-ai 调 AuthorFeign.notifyCoverGenerationFailed
4. novel-user 收到 /inner/author/ai/cover/jobs/fail
5. novel-user 调 handleCoverGenerationFailed
6. novel-user 根据 requestId 调统一 rollbackPoints
```

回滚仍然复用 `author_points_tx` 中的原扣减事务。重复回调、重复回滚会被 `requestId + ":ROLLBACK"` 幂等键拦截。

这种设计的好处是：

- AI 服务不直接掌握积分语义。
- 回滚逻辑只有一个权威入口。
- 失败触发点可以有多个，但实际回滚路径只有一条。
- 后续如果要改积分规则，只改 `novel-user`。

## Micrometer 指标

为了判断 `3 + 20` 是否合适，当前给线程池和任务增加了 Micrometer 指标。

### 线程池 Gauge

注册位置：`ImageGenerationExecutorConfiguration`

| 指标 | 含义 |
|---|---|
| `novel.ai.image.executor.active` | 当前正在执行生图任务的线程数 |
| `novel.ai.image.executor.pool.size` | 当前线程池大小 |
| `novel.ai.image.executor.pool.core` | 当前 corePoolSize |
| `novel.ai.image.executor.pool.max` | 当前 maxPoolSize |
| `novel.ai.image.executor.queue.size` | 当前排队任务数 |
| `novel.ai.image.executor.queue.remaining` | 队列剩余容量 |
| `novel.ai.image.executor.completed` | 线程池已完成任务数 |

这些指标服务启动后就会出现。

### 任务 Counter / Timer

注册位置：`ImageAsyncGenerationService`

| 指标 | 含义 |
|---|---|
| `novel.ai.image.job.submitted` | 成功提交到线程池的任务数 |
| `novel.ai.image.job.rejected{reason=...}` | 被拒绝的任务数 |
| `novel.ai.image.job.completed{status=..., failure_category=...}` | 任务完成数，按成功/失败和失败类型区分 |
| `novel.ai.image.job.duration{status=..., failure_category=...}` | 任务耗时 |

拒绝原因：

```text
saturated_precheck   容量预检查发现线程池已满
executor_rejected    submit 时被 AbortPolicy 拒绝
```

失败类型来自异常分类：

```text
transient
bad_request
auth_or_quota
content_safety
unknown
```

任务类 Counter/Timer 只有发生过对应事件后才会出现在 `/actuator/metrics` 列表里。

## Actuator 查看方式

Nacos 中 `novel-common-base.yml` 已暴露 Actuator：

```yaml
management:
  endpoints:
    web:
      exposure:
        include: "*"
```

`novel-ai-service` 本地端口为：

```yaml
server:
  port: 9070
```

访问：

```text
http://localhost:9070/actuator/health
http://localhost:9070/actuator/metrics
```

查看具体指标：

```text
http://localhost:9070/actuator/metrics/novel.ai.image.executor.active
http://localhost:9070/actuator/metrics/novel.ai.image.executor.queue.size
http://localhost:9070/actuator/metrics/novel.ai.image.executor.queue.remaining
http://localhost:9070/actuator/metrics/novel.ai.image.job.rejected
http://localhost:9070/actuator/metrics/novel.ai.image.job.duration
```

Actuator 返回 JSON，不是 HTML 页面。浏览器看起来像空白时，可以在 DevTools 的 Sources 或 Network Response 中查看，也可以使用 curl。

## 如何解读指标

### 线程池打满

```text
active 接近 max
queue.size 持续升高
queue.remaining 持续降低
```

含义：线程池执行能力不足，任务开始排队。

### 已经开始拒绝用户

```text
increase(novel.ai.image.job.rejected[5m]) > 0
```

含义：线程池和队列都已满，新的生图请求被快速拒绝。

如果没有 Prometheus，可以手动刷新 Actuator 的 `job.rejected` 指标。

### DashScope 变慢

```text
active 不高
queue.size 不高
job.duration p95/p99 升高
```

含义：瓶颈不在本地线程池，可能是模型服务、网络或 COS 转存变慢。

### 不应该扩线程的情况

```text
failure_category=auth_or_quota 增多
failure_category=content_safety 增多
```

这类问题不是并发不足。鉴权/余额/配额需要运维处理；内容安全需要提示词或前置校验优化。

### 可能需要扩线程的情况

```text
active 长期等于 max
queue.size 经常接近 queueCapacity
rejected 增长
DashScope 未明显限流
```

这时可以考虑小幅提高 `corePoolSize/maxPoolSize`，但要同时观察外部模型限流和失败率。

## 动态调整策略

当前推荐策略是“指标观测 + Nacos 调参 + 滚动重启”。

原因：

- `corePoolSize` 和 `maxPoolSize` 可以运行时改。
- `queueCapacity` 对 `ThreadPoolTaskExecutor` 来说创建后基本固定，不能安全直接改。
- 生图是付费链路，运行时随意 resize 风险高。

因此第一阶段先用 Nacos 调整配置，再滚动重启实例：

```yaml
novel:
  ai:
    image-gen:
      core-pool-size: 3
      max-pool-size: 3
      queue-capacity: 20
      keep-alive-seconds: 60
```

建议调参方式：

- 先观察 `active / queue.size / rejected / duration`。
- 如果拒绝率高且 DashScope 没有限流，再小幅增加线程数。
- 如果 429、限流、瞬时失败上升，不要继续扩线程。
- 队列不是越大越好，队列过大会让用户等很久，还会掩盖真实过载。

后续如果确实需要无重启调参，可以新增一个受控管理入口，只允许调整 `corePoolSize/maxPoolSize`，不动态调整 `queueCapacity`。调整顺序应为：

```text
调大：先 max，再 core
调小：先 core，再 max
```

## 为什么还保留 AI 服务全局限流

生图链路有多层保护：

```text
作者维度限流
  -> AI 服务全局 Redis 令牌桶
  -> 生图线程池有界队列
  -> DashScope 自身配额/限流
```

这些不是重复，而是保护层次不同：

- 作者维度限流防止单个作者刷接口。
- AI 全局令牌桶防止整个服务对模型侧打太猛。
- 生图线程池保护本进程资源和排队长度。
- DashScope 配额是外部硬限制。

线程池是最后的本地背压，不应该替代上游限流。

## 当前设计的边界与后续方向

当前实现仍然使用 JVM 内存线程池队列。它适合“轻量异步 + 前端轮询”的场景，但有一个边界：

```text
服务重启时，线程池中的排队/执行任务不会自动恢复。
```

Redis 中可能残留 `QUEUED / GENERATING / UPLOADING` 状态。若要进一步增强可靠性，可以做：

- 启动时扫描长时间停留在中间状态的任务，标记失败并通知 `novel-user` 回滚。
- 将任务队列从 JVM 内存迁移到 Redis Stream / RocketMQ / DB 任务表。
- 用定时补偿任务保证失败通知最终送达。

但在当前阶段，异步线程池方案改动小、用户体验好、背压清晰，配合 requestId 幂等回滚，已经能覆盖主要线上风险。
