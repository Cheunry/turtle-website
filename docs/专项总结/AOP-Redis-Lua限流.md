# AOP + Redis + Lua 令牌桶限流

## 1. 背景

项目里的 AIGC 能力包括 AI 审核、文本润色、流式润色、封面提示词生成和 AI 生图。这类接口有几个特点：

- 调用链路长，背后依赖大模型或文生图服务；
- 单次请求成本高，耗时也比普通 CRUD 长；
- 第三方模型通常有 QPS、并发数、队列长度等配额限制；
- 如果只靠网关做粗粒度限流，无法区分具体 AI 场景和真实资源消耗。

所以项目采用了分层保护：

```text
作者服务：按用户 + AI 场景限流，防止单用户刷接口
AI 服务内部：Redis 令牌桶 + Semaphore + 有界队列，保护模型调用和生图资源
```

其中 **AOP + Redis + Lua 令牌桶限流** 主要用于作者服务和 AI 服务内部的业务级限流。

## 2. 为什么用 AOP

限流属于横切逻辑，和日志、鉴权、幂等类似，不应该散落在每个业务方法里。

如果不用 AOP，每个接口都要手写：

```java
if (!rateLimiter.tryAcquire("polish")) {
    return RestResp.fail(...);
}
return textService.polishText(req);
```

这样会带来几个问题：

- 重复代码多，审核、润色、生图等接口都要写一遍；
- 业务代码被 Redis、Lua、限流错误码污染；
- 统一修改策略困难，比如要调整超限响应或 Redis key 规则时容易漏改；
- 新增 AI 能力时接入限流成本高。

使用 AOP 后，业务侧只需要声明限流场景：

```java
@AiRateLimit(AiRateLimitScene.POLISH)
public RestResp<TextPolishRespDto> polishText(...) {
    return textService.polishText(req);
}
```

真正的限流执行逻辑统一收口在切面中。这样业务代码保持干净，基础设施逻辑也更容易维护。

## 3. 为什么用 Redis + Lua

令牌桶的核心逻辑包括：

1. 读取当前桶内令牌数；
2. 根据上次刷新时间计算应该补充多少令牌；
3. 判断当前请求是否能消耗 1 个令牌；
4. 扣减令牌并写回状态；
5. 设置过期时间。

这些步骤必须是原子的。如果拆成多条 Redis 命令，高并发下可能出现多个请求同时读到旧令牌数，导致超发。

Lua 脚本在 Redis 内部单线程原子执行，可以把“读、算、扣、写”一次完成，因此适合做分布式限流。

另外，脚本里使用 Redis 的 `TIME` 命令作为时间来源，而不是使用应用服务器本地时间。这样多实例部署时，各个 JVM 的机器时钟不一致也不会影响同一个限流桶的计算。

## 4. 算法选择

项目用的是 **令牌桶算法 Token Bucket**，不是固定窗口，也不是滑动窗口。

固定窗口实现简单，但窗口边界可能出现流量翻倍。例如限制每分钟 60 次，用户可以在 00:59 打 60 次，在 01:00 又打 60 次，瞬间形成 120 次突发。

令牌桶的特点是：

- 以固定速率补充令牌；
- 请求来了必须先拿到令牌；
- 桶满后不再继续累积；
- 可以限制平均 QPS，同时允许一定短时突发；
- 比固定窗口更平滑。

项目中的配置含义是：

```text
桶容量 = max-permits
补充速率 = max-permits / window-seconds
每次请求消耗 1 个令牌
```

例如：

```yaml
max-permits: 30
window-seconds: 60
```

表示该场景桶容量为 30，平均每 2 秒补充 1 个令牌。

## 5. Redis 存储结构

每个限流桶在 Redis 中使用 Hash 存储：

```text
tok：当前剩余令牌数
ts：上次刷新时间戳
```

AI 服务内部全局限流的 key 形如：

```text
ratelimit:{ai:global}:tb:scene:polish
ratelimit:{ai:global}:tb:scene:image_generate
```

作者服务按用户维度限流的 key 形如：

```text
ratelimit:{author:ai:cover}:tb:user:{userId}
ratelimit:{author:ai:audit}:tb:user:{userId}
ratelimit:{author:ai:polish}:tb:user:{userId}
```

其中 `{...}` 是 Redis Cluster hash tag，用来保证相关 key 在集群模式下落到同一个 slot。

## 6. Lua 脚本逻辑

核心伪代码如下：

```lua
capacity = ARGV[1]
refill_per_sec = ARGV[2]

now = redis.call('TIME')
tok, ts = HMGET key tok ts

if tok == nil then
  tok = capacity
  ts = now
end

elapsed = now - ts
tok = min(capacity, tok + elapsed * refill_per_sec)

if tok >= 1 then
  tok = tok - 1
  allowed = 1
else
  allowed = 0
end

HSET key tok ts
EXPIRE key ttl
return allowed
```

返回值含义：

```text
1：允许通过
0：触发限流
-1：脚本参数非法，业务侧降级放行并打 warn 日志
```

## 7. 作者服务中的实现

作者服务做的是 **按用户 + AI 场景** 的限流，主要防止单个用户频繁调用高成本 AI 能力。

相关代码：

```text
novel-user/novel-user-service/src/main/java/com/novel/user/ratelimit/annotation/AuthorAiRateLimit.java
novel-user/novel-user-service/src/main/java/com/novel/user/ratelimit/AuthorAiRateLimitScene.java
novel-user/novel-user-service/src/main/java/com/novel/user/ratelimit/config/AuthorAiRateLimitProperties.java
novel-user/novel-user-service/src/main/java/com/novel/user/ratelimit/aspect/AuthorAiRateLimitAspect.java
```

使用位置：

```java
@PostMapping("ai/audit")
@AuthorAiRateLimit(AuthorAiRateLimitScene.AUDIT)

@PostMapping("ai/polish")
@AuthorAiRateLimit(AuthorAiRateLimitScene.POLISH)

@PostMapping("ai/cover")
@AuthorAiRateLimit(AuthorAiRateLimitScene.COVER_IMAGE)
```

切面会从 `UserHolder.getUserId()` 获取当前用户 ID，然后拼出用户维度的 Redis key。

当前默认配置：

```yaml
novel:
  user:
    ratelimit:
      author-ai:
        cover-image:
          enabled: true
          max-permits: 1
          window-seconds: 60
        audit:
          enabled: true
          max-permits: 1
          window-seconds: 60
        polish:
          enabled: true
          max-permits: 1
          window-seconds: 10
```

也就是：

```text
封面生图：每个用户 60 秒 1 次
AI 审核：每个用户 60 秒 1 次
AI 润色：每个用户 10 秒 1 次
```

## 8. AI 服务内部的实现

AI 服务内部做的是 **按场景全局限流**，用于保护模型调用和生图资源。它不关心请求来自作者服务、书籍服务还是其他内部服务，只要进入 AI 服务的高成本接口，就会经过这一层限流。

相关代码：

```text
novel-ai/novel-ai-service/src/main/java/com/novel/ai/ratelimit/annotation/AiRateLimit.java
novel-ai/novel-ai-service/src/main/java/com/novel/ai/ratelimit/AiRateLimitScene.java
novel-ai/novel-ai-service/src/main/java/com/novel/ai/ratelimit/config/AiRateLimitProperties.java
novel-ai/novel-ai-service/src/main/java/com/novel/ai/ratelimit/aspect/AiRateLimitAspect.java
```

使用位置：

```java
@AiRateLimit(AiRateLimitScene.AUDIT_BOOK)
public RestResp<BookAuditRespDto> auditBook(...)

@AiRateLimit(AiRateLimitScene.POLISH)
public RestResp<TextPolishRespDto> polishText(...)

@AiRateLimit(AiRateLimitScene.IMAGE_GENERATE)
public RestResp<ImageGenJobSubmitRespDto> generateImage(...)
```

目前覆盖的场景：

```text
AUDIT_BOOK：书籍审核
AUDIT_CHAPTER：章节审核
COVER_PROMPT：封面提示词生成
POLISH：文本润色
POLISH_STREAM：流式润色
IMAGE_GENERATE：生图提交
AUDIT_RULE_EXTRACT：审核规则提取
```

Nacos 中配置：

```text
nacos/local/DEFAULT_GROUP/novel-ai-service-dev.yml
nacos/prod/DEFAULT_GROUP/novel-ai-service-prod.yml
```

示例：

```yaml
novel:
  ai:
    ratelimit:
      audit-book:
        enabled: true
        max-permits: 30
        window-seconds: 60
      audit-chapter:
        enabled: true
        max-permits: 60
        window-seconds: 60
      polish:
        enabled: true
        max-permits: 120
        window-seconds: 60
      image-generate:
        enabled: true
        max-permits: 30
        window-seconds: 60
```

## 9. 与 Semaphore / 有界队列的关系

AI 服务内部不是只靠 Redis 令牌桶。

审核 Pipeline 还有 `Semaphore` 控制并发数：

```text
novel.ai.audit.max-concurrent=16
```

生图任务还有专用线程池和有界队列：

```text
novel.ai.image-gen.core-pool-size=3
novel.ai.image-gen.max-pool-size=3
novel.ai.image-gen.queue-capacity=20
```

三者职责不同：

```text
Redis 令牌桶：限制单位时间进入 AI 服务的请求量，解决 QPS 和突发流量
Semaphore：限制同时运行中的审核 Pipeline 数量，解决执行并发
有界队列：限制生图任务排队长度，解决任务堆积和背压
```

也就是说，令牌桶负责“别进来太快”，Semaphore 和有界队列负责“进来了也别把服务拖垮”。

## 10. 按 Token 用量限流

前面的令牌桶解决的是“调用次数”问题，但大模型调用还有一个更核心的成本指标：Token。

同样是一次 AI 调用，不同请求的成本差异很大：

```text
短文本润色：可能只消耗几百 token
长章节审核：可能消耗几千甚至上万 token
章节分段审核：一次业务请求背后可能触发多次模型调用
结构化输出修复重试：解析失败时可能再次调用模型
```

所以如果只按“调用 1 次扣 1 个令牌”限流，就无法防止短时间内消耗大量 token，进而导致模型费用快速上升。

项目里新增了一层 **Token 预算令牌桶**，位置不放在 Controller AOP，而是放在 Spring AI `ChatClient` 的 Advisor 链上。

原因是 Controller 层只能看到一次接口请求，但不能准确知道背后真正发生了几次模型调用。比如章节审核会按内容分段，每一段都会调用一次大模型；结构化输出失败时也可能触发修复型 Prompt 重试。把 token 限流放到 `ChatClient` 层，可以覆盖每一次真实模型调用。

相关代码：

```text
novel-ai/novel-ai-service/src/main/java/com/novel/ai/ratelimit/advisor/AiTokenRateLimitAdvisor.java
novel-ai/novel-ai-service/src/main/java/com/novel/ai/ratelimit/AiTokenBucketRateLimiter.java
novel-ai/novel-ai-service/src/main/java/com/novel/ai/ratelimit/config/AiTokenRateLimitProperties.java
novel-ai/novel-ai-service/src/main/java/com/novel/ai/ratelimit/AiTokenUsageLogService.java
```

### 10.1 预扣 + 结算

Token 限流采用的是 **预扣 + 结算**。

调用模型前，先估算本次调用的 token 成本：

```text
预估总 token = 预估输入 token + 预留输出 token
```

然后用 Redis Lua token 桶按这个成本预扣 token。桶里 token 不够时，直接拒绝本次模型调用，返回 AI 服务限流错误。

模型调用成功后，再从 Spring AI 返回的 `Usage` 中读取真实 token：

```java
ChatResponse chatResponse = response.chatResponse();
Usage usage = chatResponse.getMetadata().getUsage();
```

拿到真实 token 后进行结算：

```text
实际消耗 < 预扣 token：退还差额
实际消耗 > 预扣 token：补扣差额
```

这样既能在调用前做成本保护，又能在调用后尽量让 Redis 桶状态贴近真实消耗。

### 10.2 输入 Token 怎么估算

输入 token 不是只看用户提交的正文，而是看最终发给模型的 prompt。

最终 prompt 通常包括：

```text
system prompt
user prompt
结构化输出格式说明
分类审核规则
RAG 注入内容
用户提交的正文
```

所以项目在 `ChatClient` Advisor 中读取最终 `Prompt.getContents()`，再做启发式估算。

当前估算规则：

```text
ASCII 字符：约 4 个字符 = 1 token
中文/非 ASCII 字符：约 1 个字符 = 0.7 token
再乘安全系数 estimate-safety-factor，当前为 1.25
```

这个估算不追求完全精确，目标是调用前不要明显低估。真正精确的值以后以模型返回的 `Usage` 为准。

### 10.3 输出 Token 怎么预留

输出 token 在模型生成前无法精确知道，所以按业务场景做预留。

不同场景的特点不同：

```text
审核：输入可能很大，但输出只是结构化 JSON，输出较小
润色：输入中等，输出通常接近输入长度
封面提示词：输入小，输出也小
规则提取：输入中等，输出中等
```

因此 Nacos 中按场景配置输出预留策略：

```yaml
novel:
  ai:
    token-ratelimit:
      scenes:
        audit-book:
          completion-reserve-tokens: 1024
          completion-input-ratio: 0
          min-completion-reserve-tokens: 512
          max-completion-reserve-tokens: 2048
        audit-chapter:
          completion-reserve-tokens: 1024
          completion-input-ratio: 0
          min-completion-reserve-tokens: 512
          max-completion-reserve-tokens: 2048
        polish:
          completion-reserve-tokens: 1024
          completion-input-ratio: 1.2
          min-completion-reserve-tokens: 1024
          max-completion-reserve-tokens: 8192
        cover-prompt:
          completion-reserve-tokens: 512
          completion-input-ratio: 0
          min-completion-reserve-tokens: 256
          max-completion-reserve-tokens: 1024
        audit-rule-extract:
          completion-reserve-tokens: 2048
          completion-input-ratio: 0
          min-completion-reserve-tokens: 1024
          max-completion-reserve-tokens: 4096
```

例如审核场景：

```text
预扣 token = promptEstimatedTokens + 1024
```

润色场景：

```text
预扣 token = promptEstimatedTokens + max(1024, promptEstimatedTokens * 1.2)
```

这样审核不会因为输入长就过度预留输出，润色也不会因为输出接近原文而明显低估。

### 10.4 Token 桶配置

项目使用的通义千问对话模型 TPM 是 500 万，但应用侧不是按满配额放开，而是按成本预算控制为四分之一：

```text
1,250,000 tokens / minute
```

配置如下：

```yaml
novel:
  ai:
    token-ratelimit:
      enabled: true
      fail-open: true
      default-model: default-text-model
      default-completion-reserve-tokens: 4096
      estimate-safety-factor: 1.25
      models:
        default-text-model:
          enabled: true
          capacity-tokens: 1250000
          window-seconds: 60
```

这里的 `capacity-tokens / window-seconds` 就是 token 桶补充速率。它不是为了贴满供应商配额，而是为了限制业务短时间烧太多钱。

### 10.5 Token 使用流水

每次真实模型调用都会异步写入 token 使用流水表：

```text
ai_token_usage_log
```

记录字段包括：

```text
scene：业务场景
model：模型名称
estimated_prompt_tokens：预估输入 token
reserved_completion_tokens：预留输出 token
estimated_total_tokens：预估总 token
actual_prompt_tokens：真实输入 token
actual_completion_tokens：真实输出 token
actual_total_tokens：真实总 token
estimate_delta_tokens：真实总 token - 预估总 token
status：SUCCESS / FAILED / RATE_LIMITED
duration_ms：模型调用耗时
```

这张表不参与实时限流，只用于成本复盘和调参。

实时扣减放在 Redis，是因为 Redis Lua 原子性好、延迟低；数据库只做异步记录，避免影响模型调用主链路。

后续可以根据流水表统计每个场景的预估偏差：

```sql
SELECT
  scene,
  COUNT(*) AS call_count,
  AVG(estimated_total_tokens) AS avg_estimated,
  AVG(actual_total_tokens) AS avg_actual,
  AVG(estimate_delta_tokens) AS avg_delta
FROM ai_token_usage_log
WHERE status = 'SUCCESS'
  AND created_at >= DATE_SUB(NOW(), INTERVAL 7 DAY)
GROUP BY scene;
```

如果某个场景长期高估，可以降低输出预留；如果长期低估，可以提高 `completion-input-ratio` 或安全系数。

## 11. 超限与异常策略

普通接口超限时返回统一业务错误码：

```java
AI_SERVICE_RATE_LIMIT("C4004", "AI服务请求过于频繁，请稍后再试")
```

作者端用户维度限流使用：

```java
AI_AUTHOR_RATE_LIMIT("C4003", "操作过于频繁，请稍后再试")
AI_IMAGE_GENERATION_BUSY("C4002", "当前生图任务较多，请稍后再试")
```

如果是 SSE 流式接口，切面会返回一个短生命周期的 `SseEmitter`，发送 `error` 事件后关闭连接，保证前端仍然按 SSE 协议收到错误。

Redis 脚本异常时，策略是 **降级放行并打 warn 日志**。

原因是限流组件不能成为核心链路的单点故障。如果 Redis 短暂异常，后面仍然有 Semaphore 和有界队列兜底，比直接让 AI 服务全部不可用更稳。

## 12. 面试回答版本

可以这样回答：

> 我在项目里针对 AIGC 服务做了分层限流。作者服务按用户和 AI 场景做细粒度限流，AI 服务内部再做一层全局场景限流。另外，因为大模型成本主要和 token 有关，我还做了模型调用层的 token 预算限流。
>
> 具体实现上，我用 Spring AOP 定义了限流注解，比如 `@AiRateLimit(AiRateLimitScene.POLISH)`，标在审核、润色、生图这些高成本接口上。切面拦截后读取场景配置，计算令牌补充速率，然后执行 Redis Lua 脚本完成令牌桶判断。
>
> Redis 中用 Hash 存桶状态，字段包括当前令牌数 `tok` 和上次刷新时间 `ts`。Lua 脚本使用 Redis `TIME` 命令作为统一时间源，然后原子完成令牌补充、扣减和写回。这样在多实例部署下也能保证限流状态一致，不会出现并发超发。
>
> 我选令牌桶而不是固定窗口，是因为令牌桶可以限制平均 QPS，同时允许一定短时突发，并且不会出现固定窗口边界流量翻倍的问题。
>
> AI 服务内部这层限流是按场景做全局保护，比如 `polish`、`image_generate`，Redis key 类似 `ratelimit:{ai:global}:tb:scene:polish`。配置放在 Nacos 里，本地和生产可以分别调整 `max-permits` 和 `window-seconds`。
>
> 但是调用次数限流只能控制 QPS，不能控制单次请求到底消耗多少 token。所以我又在 Spring AI `ChatClient` 的 Advisor 链上加了一层 token 预算桶。之所以放在 ChatClient 层，是因为一次接口调用不一定等于一次模型调用，例如章节审核会分段，结构化输出失败也可能重试。
>
> Token 限流采用预扣 + 结算。调用模型前先按最终 prompt 估算输入 token，再按业务场景预留输出 token，然后用 Redis Lua 按估算成本预扣。模型调用成功后，从 Spring AI 的 `ChatResponse.metadata.usage` 中拿真实 token，用真实值和预扣值做多退少补。
>
> 输出预留按场景配置：审核输入可能很大但输出只是 JSON，所以输出预留较小；润色输出接近输入，所以按输入 token 的比例预留；封面提示词和规则提取再分别配置。当前通义千问对话模型 TPM 是 500 万，但我为了控制成本，应用侧 token 桶只配置到四分之一，也就是每分钟 125 万 token。
>
> 同时我把每次模型调用的预估 token、真实 token、差值、场景和耗时异步写入 `ai_token_usage_log`，后续可以按场景统计预估偏差，再反向调整 Nacos 中的预估参数。
>
> 除了 Redis QPS 限流，我还保留了审核 Pipeline 的 Semaphore 并发闸门，以及生图线程池的有界队列。令牌桶控制请求进入速度，Semaphore 控制运行中并发，有界队列控制任务堆积。三层叠加后，可以更好地保护模型资源和服务稳定性。

## 13. 常见追问

### 13.1 为什么不用固定窗口？

固定窗口实现简单，但窗口边界会有突刺。比如每分钟限制 60 次，用户可能在上一分钟最后 1 秒请求 60 次，下一分钟第一秒又请求 60 次，实际瞬时流量达到 120 次。令牌桶按速率补充令牌，流量更平滑。

### 13.2 为什么不用本地 Guava RateLimiter？

本地限流只能限制单个 JVM。项目是微服务多实例部署，如果 AI 服务扩容到多个实例，本地限流会变成每个实例各限一份，总 QPS 不可控。Redis 令牌桶是所有实例共享同一个限流状态，更适合分布式场景。

### 13.3 为什么 Lua 能保证原子性？

Redis 执行 Lua 脚本时，脚本内部的多条命令作为一个整体执行，中间不会被其他请求插入。所以读令牌、补充令牌、扣减令牌、写回状态这些操作可以保证原子性。

### 13.4 为什么用 Redis TIME？

多实例部署时，各个应用服务器的本地时钟可能有偏差。用 Redis `TIME` 可以让同一个限流 key 使用同一个时间源，避免因为机器时间不一致导致令牌补充异常。

### 13.5 为什么 Redis 异常时降级放行？

限流是保护手段，不应该成为主链路的单点故障。如果 Redis 短暂异常，直接拒绝所有请求会影响可用性。项目里 Redis 限流异常时会打 warn 并放行，后面还有 Semaphore 和有界队列继续兜底。

### 13.6 AI 服务内部限流和作者服务限流有什么区别？

作者服务限流是按用户维度，主要防止某个用户频繁调用 AI 能力。

AI 服务内部限流是按场景全局维度，主要保护模型资源。即使请求不是从作者服务进来的，只要打到 AI 服务接口，也会被这层限流保护。

### 13.7 为什么还需要 Semaphore 和有界队列？

Redis 令牌桶控制的是单位时间请求量，但它不直接控制当前有多少任务正在运行，也不控制生图任务排队长度。

Semaphore 控制审核任务同时运行数量；有界队列控制生图任务最多能排多少。三者互补，才能避免高成本任务无限堆积。

### 13.8 如何动态调整限流阈值？

限流参数通过 `@ConfigurationProperties` 绑定，配置前缀是：

```text
novel.ai.ratelimit
novel.user.ratelimit.author-ai
```

实际运行时本地和云端都使用 Nacos 配置，所以可以在 Nacos 中调整每个场景的 `enabled`、`max-permits`、`window-seconds`。

### 13.9 为什么 token 限流不放在 Controller AOP？

Controller AOP 只能看到一次接口请求，但看不到背后的真实模型调用次数。

例如章节审核可能切成多段，每段都要调用一次模型；结构化输出解析失败时，还可能用修复型 Prompt 再调用一次。如果在 Controller 层按一次请求扣一次 token，就会低估真实成本。

所以 token 限流放在 `ChatClient` Advisor 层，每次真实模型调用前都做预扣和结算。

### 13.10 真实 token 从哪里来？

真实 token 来自模型返回的 usage 信息。

在 Spring AI 中可以通过：

```java
ChatResponse chatResponse = response.chatResponse();
Usage usage = chatResponse.getMetadata().getUsage();
```

拿到：

```text
promptTokens：输入 token
completionTokens：输出 token
totalTokens：总 token
```

非流式调用一般能拿到 usage。流式调用是否能拿到完整 usage 取决于底层模型 SDK 是否在最后一帧返回 usage，因此项目目前主要针对非流式对话模型调用做真实 token 结算。

### 13.11 为什么还要落库？

Redis 适合实时限流，但不适合长期分析。

Token 使用流水落库后，可以按场景统计预估值和真实值的差异。例如审核场景长期高估，就降低 `completion-reserve-tokens`；润色场景长期低估，就提高 `completion-input-ratio`。

因此 Redis 负责实时挡流，数据库负责复盘和调参。
