# AI 服务模块详解（面试版）

本文档聚焦 `novel-ai-service`，用于说明 AI 审核、润色、封面生成与消息协同链路。

## 1. 模块定位

`novel-ai` 在系统中的核心定位是“AI 能力中台”，承担三类能力：

- **内容审核**：书籍/章节审核（AI 初审）
- **创作增强**：文本润色、封面提示词生成
- **素材生成**：根据提示词生成封面图片

一句话概括：`novel-ai` 把模型调用封装成稳定的业务能力，对上层屏蔽模型细节和异常处理复杂度。

## 2. 边界与协作

## 2.1 上游

- `novel-user`：作者侧 AI 功能入口（审核、润色、封面）
- `novel-book`：通过 MQ 发送书籍/章节审核请求

## 2.2 下游/外部

- DashScope（通义）模型服务：
  - 文本模型：`text_model`
  - 文生图模型：`qwen-image-plus`
- COS（可选）：图片转存，避免临时 URL 过期
- RocketMQ：审核请求/审核结果异步通信

## 2.3 核心依赖（`pom.xml`）

- `spring-ai-alibaba-starter-dashscope`
- `spring-ai-client-chat`
- `rocketmq-spring-boot-starter`
- `apm-toolkit-trace`（SkyWalking 埋点）

## 3. 服务启动与基础配置

`NovelAiApplication` 关键能力开关：

- `@EnableRetry`：开启重试机制（图片生成场景关键）
- `@EnableFeignClients`：支持跨服务调用
- `@EnableCaching`：支持缓存扩展能力

`AiConfig` 负责注入：

- `ChatClient`（文本能力）
- `ImageModel`（图片能力）

## 4. 对外能力入口

## 4.1 前台接口 `FrontAiController`

- `POST /api/front/ai/polish`：文本润色
- `POST /api/front/ai/cover-prompt`：生成封面提示词
- `POST /api/front/ai/generate-image`：根据提示词生成图片

## 4.2 内部接口 `InnerAiController`

- `POST /api/inner/ai/audit/book`：书籍审核
- `POST /api/inner/ai/audit/chapter`：章节审核
- `POST /api/inner/ai/generate/image/prompt`：封面提示词
- `POST /api/inner/ai/polish`：文本润色
- `POST /api/inner/ai/generate/image`：生成图片

## 4.3 辅助调试入口 `ChatController`

- `/api/front/ai/chat`
- `/api/front/ai/image`

说明：该控制器更偏调试/实验用途，生产建议控制访问范围。

## 5. 核心能力实现

## 5.1 审核能力（`TextServiceImpl`）

### 书籍审核 `auditBook()`

- 构建结构化审核提示词（名称+简介）
- 调用 ChatClient
- 解析 JSON 格式审核结果
- 返回：`auditStatus`、`aiConfidence`、`auditReason`

异常策略：

- 检测到 `DataInspectionFailed` / 不当内容：直接标记不通过
- 其他异常：返回待审核（人工兜底）

### 章节审核 `auditChapter()`

- 内容为空：直接待审核
- 长文本按 5000 字分段审核
- 分段结果合并规则：
  - 任一段不通过 -> 总体不通过
  - 无不通过但存在待审核段 -> 总体待审核
  - 全部通过 -> 总体通过
- 置信度按段求平均
- 审核原因按优先级合并并做长度截断（防止 DB 字段溢出）

## 5.2 润色能力 `polishText()`

- 入参严格校验（最小/最大长度）
- 构建固定 JSON 输出格式提示词
- 解析失败时降级：返回原始文本 + 解析失败说明

## 5.3 封面能力

### 提示词生成 `getBookCoverPrompt()`

- 校验简介最小长度（防止信息不足）
- 通过模板提示词让模型输出“可生图视觉描述”
- 自动追加后缀（高品质、无文字、无水印等）

### 图片生成 `ImageServiceImpl.generateImage()`

- 使用 `@Retryable` 自动重试（最多 15 次，指数退避）
- 生成成功后优先转存 COS（若已配置）
- COS 转存失败不阻断主流程，回退临时 URL

## 6. MQ 审核链路（关键）

## 6.1 请求与结果主题映射

- 审核请求 Topic：`topic-book-audit-request`
  - 书籍请求 Tag：`audit_book_request`
  - 章节请求 Tag：`audit_chapter_request`
- 审核结果 Topic：`topic-book-audit-result`
  - 书籍结果 Tag：`audit_book_result`
  - 章节结果 Tag：`audit_chapter_result`

## 6.2 消费者实现

- `BookAuditRequestListener`：消费书籍审核请求，调用文本审核后回发结果
- `ChapterAuditRequestListener`：消费章节审核请求，调用章节审核后回发结果

设计要点：

- 即使审核失败也会回发失败结果，保证业务服务可感知
- 结果消息包含 `success` 与 `errorMessage`，便于下游做状态分支

## 7. 可观测与稳定性设计

## 7.1 SkyWalking 埋点

在核心方法打了 `@Trace` 和标签：

- 模型名、操作类型
- prompt 长度、响应长度
- 调用耗时
- 异常类型与错误信息（截断）

这让你在排障时能快速分辨“模型慢”还是“业务解析慢”。

## 7.2 稳定性策略

- 审核异常不直接硬失败，回落到待人工审核
- 图片生成具备重试能力
- COS 非强依赖，失败可回退临时 URL
- JSON 解析有兜底逻辑（Jackson + 正则提取）

## 8. 典型时序（可背诵）

## 8.1 书籍审核时序

1. `novel-book` 发送 `BookAuditRequestMq`
2. `BookAuditRequestListener` 消费并调用 `TextService.auditBook`
3. AI 返回审核结果（通过/不通过/待审核）
4. AI 服务发送 `BookAuditResultMq`
5. `novel-book` 消费结果并更新审核状态

## 8.2 作者生成封面时序

1. `novel-user` 调用 AI 服务生成提示词
2. 调用图片生成接口
3. AI 返回临时图片 URL
4. 可选：转存 COS，返回持久化 URL

## 9. 常见故障与排查

## 9.1 症状：审核一直待审核

排查顺序：

1. 审核请求 MQ 是否发送成功
2. AI 请求消费者是否消费成功
3. 是否命中内容安全拦截/模型异常
4. 审核结果 MQ 是否成功回发

## 9.2 症状：封面生成经常失败

排查顺序：

1. 观察重试日志是否达到上限
2. 检查 prompt 是否触发内容安全拦截
3. 检查 DashScope 限流/网络问题
4. 检查 COS 转存是否失败（与生图成功区分开）

## 9.3 症状：响应很慢

排查顺序：

1. SkyWalking 看 AI 调用耗时
2. 判断是文本审核慢还是图片生成慢
3. 长章节是否触发了分段审核
4. 是否存在连续重试导致总时长上升

## 10. 当前优势与风险

## 10.1 优势

- 业务接口友好：统一返回结构，便于上游编排
- 容错完整：审核失败可回落人工审核
- 可观测性强：关键调用全链路埋点
- 工程化成熟：MQ 双向异步，减少同步耦合

## 10.2 风险

- 强依赖外部模型 SLA，峰值期可能抖动
- 提示词工程复杂，模型输出格式仍可能漂移
- 审核与图片链路缺少统一任务状态中心（目前更多靠日志）
- 调试接口若未收敛，存在暴露风险

## 11. 可落地优化建议

1. 增加“审核任务状态表”，串起请求->调用->结果全链路  
2. 把解析失败样本落库，建立 prompt/响应回归用例  
3. 为图片生成增加异步任务模式（提交后轮询）以减少请求超时  
4. 对高频提示词增加缓存与模板版本管理  
5. 为 MQ 失败路径增加 DLQ 告警与自动补偿任务  

## 12. 模型选型与配置

## 12.1 为什么选当前文本模型做文本审核/润色

| 对比项 | 文本模型 | 其他方案（如 GPT-4） |
|--------|-----------|---------------------|
| **中文理解** | 对中文小说内容优化，敏感词识别准确 | 一般 |
| **成本** | 国内计费，整体成本低 | 较高，汇率波动 |
| **延迟** | 国内节点，P50 在 1~3s | 国外节点，延迟更高 |
| **稳定性** | 阿里云基础设施 | 依赖境外服务 |
| **合规** | 数据不出境 | 存在合规风险 |

**取舍点**：对于需要高中文语言理解、高频调用、要求数据不出境的审核场景，当前文本模型是当前最合适的选择。

## 12.2 为什么选 qwen-image-plus 做文生图

- 支持 3:4（1140×1472）竖版比例，适合书籍封面
- 具备内容安全审核，会自动过滤违规 prompt
- 异步任务轮询模式，结合重试策略可覆盖 1~2 分钟生成时间

## 12.3 置信度阈值 0.8 的依据

```
auditStatus=1 && confidence >= 0.8  ->  AI 直接通过
auditStatus=1 && confidence <  0.8  ->  转人工复核
auditStatus=2                       ->  AI 不通过
```

**原因**：AI 审核本身存在不确定性。当置信度低于 0.8 时，说明模型对这条内容本身也"没把握"，此时强制通过的风险大于让人工看一眼的成本，因此转人工是更安全的兜底策略。

---

## 13. 提示词工程细节

## 13.1 审核提示词设计原则

**书籍审核 Prompt 结构**：

```
角色设定 → 输入内容 → 审核标准（5 条）→ 输出格式（强制 JSON）
```

关键设计：
- **强制 JSON 输出**：直接规定响应格式，降低解析失败概率
- **字段语义明确**：`auditStatus` 只允许 1 或 2，`aiConfidence` 范围 0.0~1.0，降低模型歧义
- **审核标准显式列举**：避免模型因理解不一致而误判

**章节审核分段提示词扩展**：

当内容分多段时，在 prompt 开头追加：

```
注意：这是章节内容的第 N/M 段，请对该段内容进行审核。
```

目的是让模型知道当前是"局部审核"，不因缺少上下文而误判。

## 13.2 润色提示词设计原则

```
角色设定（专业小说编辑）→ 待润色文本 → 风格要求 → 具体要求 → 强制 JSON 输出
```

关键约束：
- 要求模型"保持原意，提升文学性"
- 明确要分段（符合小说排版习惯）
- 限制文本长度：10~10000 字，防止超 token 或无意义输入

## 13.3 封面提示词（二次提示词）设计原则

这是**提示词的提示词**，让 AI 生成适合文生图的视觉描述，而非故事梗概：

核心指令：
- 去剧情化：不描述情节，描述**视觉画面**
- 三段式结构：核心主体 + 环境氛围 + 艺术规格
- 禁忌词：禁止文字、人名、水印（防止生图失败）

追加后缀（固定）：
```
高品质插画，书籍装帧风格，黄金比例构图，极致细节，最高解析度，(无文字，无水印：1.5)
```

目的：统一生图质量下限，无论模型回复好坏，最终 prompt 都带有质量保证词。

---

## 14. 异常矩阵（完整版）

| 场景 | 异常类型 | 当前处理 | 效果 | 风险 |
|------|----------|----------|------|------|
| 书籍/章节审核 - 内容安全拦截 | `NonTransientAiException(DataInspectionFailed)` | 直接返回 `auditStatus=2` | 立即驳回 | 极少误判风险 |
| 书籍/章节审核 - 其他模型异常 | 任意 Exception | 返回 `auditStatus=0` | 人工兜底 | 人工侧压力上升 |
| 章节审核 - 某一分段异常 | 任意 Exception | 该段标记 `auditStatus=0`，继续审其他段 | 不中断整体审核 | 局部信息缺失 |
| 审核结果 JSON 解析失败 | Exception | Jackson 失败后 regex 补救，仍失败则 `auditStatus=0` | 两层容错 | 极低概率丢字段 |
| 图片生成 - 返回 null | RuntimeException | `@Retryable` 触发重试 | 自动恢复 | 达上限则失败 |
| 图片生成 - 其他异常 | Exception | 抛出，继续触发重试 | 自动恢复 | 同上 |
| COS 转存失败 | Exception | 记录 warn 日志，回退临时 URL | 图片可用但有效期 24h | 长期链接失效 |
| 润色请求 - 参数不合规 | 主动校验 | 返回 `USER_REQUEST_PARAM_ERROR` | 及早拒绝 | 无 |
| 润色 - 解析失败 | Exception | 降级返回原始模型输出 + 说明 | 有损可用 | 格式不稳定 |
| Feign 调用 AI 服务失败 | Feign 熔断 | `AiFeignFallback` 返回指定错误码 | 上游感知失败 | 用户体验受损 |

---

## 15. 图片生成重试参数详解

```java
@Retryable(
    retryFor = {Exception.class},
    maxAttempts = 15,
    backoff = @Backoff(delay = 3000, multiplier = 1.5, maxDelay = 10000)
)
```

**参数说明与选型理由**：

| 参数 | 值 | 理由 |
|------|-----|------|
| `maxAttempts` | 15 | 按 DashScope 官方建议的轮询策略，前 30s 每 3s 一次约 10 次，之后拉长间隔，总覆盖 1~2 分钟 |
| `delay` | 3000ms | 初始等待 3s，给模型足够的初次生成时间 |
| `multiplier` | 1.5 | 指数退避，避免频繁请求触发限流 |
| `maxDelay` | 10000ms | 上限 10s，避免等待时间无限拉长 |

**预估重试时间轴**：

```
第 1 次失败 → 等 3s → 第 2 次
第 2 次失败 → 等 4.5s → 第 3 次
第 3 次失败 → 等 6.75s → 第 4 次
...
第 N 次失败 → 等 10s（上限）
总等待时间上限 ≈ (10×10) + (3+4.5+6.75) s ≈ ~115s（约 2 分钟）
```

**风险说明**：
- 每次重试都会记录一条 warn 日志，属正常现象
- 15 次全部失败时，最终抛出异常，由上层返回失败
- 接口设计上要告知调用方图片生成是"长耗时操作"，前端需配合超时处理

---

## 16. 面试高频追问（含参考回答）

### Q1：为什么审核失败不直接返回失败，而是回落待审核？

**回答要点**：

AI 审核的目标是"辅助"而非"完全替代"人工审核。当 AI 调用失败时，如果直接返回失败，会导致：
1. 作者内容被误拒，影响创作体验
2. 业务侧需要复杂的重试逻辑

而回落待审核的效果是：
- 内容进入人工审核队列，不丢失
- 对作者来说是"等待结果"而非"直接拒绝"
- 系统稳健性更高：AI 故障不影响内容上架流程，只是变慢

**例外**：当模型明确检测到不当内容（`DataInspectionFailed`）时，不能回落待审核，必须直接拒绝。

---

### Q2：长章节为什么要分段审核，合并规则怎么定？

**回答要点**：

原因：大模型有 token 上限（当前文本模型约 8192 tokens），章节内容过长会导致请求失败，因此按 5000 字分段。

合并规则设计依据"最严格的段决定整体"原则：

```
任一段 = 不通过  →  整体不通过（内容合规不能有漏网之鱼）
无不通过 + 存在待审核  →  整体待审核（不确定就保守）
全部通过  →  整体通过
```

置信度取各段平均值，代表整体"确定性"。

**面试可延伸**：合并策略也存在"假阴性"问题，即某段单独审核是好的，但合并后上下文有歧义。目前的取舍是用分段提示词中加"第 N/M 段"说明来弥补。

---

### Q3：图片生成为什么要重试，重试参数怎么定的？

**回答要点**：

DashScope 的文生图是**异步任务模型**，提交 prompt 后需轮询才能拿到结果。Spring AI 封装为同步调用后，内部可能返回 null（任务尚未完成）。

选择 `@Retryable` 而非自写轮询的理由：
- 代码简洁，AOP 方式无侵入
- Spring Retry 的退避策略开箱即用
- 无需自己维护状态机

参数选型依据 DashScope 官方文档建议的轮询策略：
- 前期短间隔（3s），覆盖快速完成的场景
- 后期长间隔（≤10s），覆盖慢速场景
- 最大 15 次，约覆盖 2 分钟，超过则放弃

---

### Q4：COS 转存失败为什么不让接口失败？

**回答要点**：

这是一个"核心能力与增强能力解耦"的设计决策：

- **核心能力**：AI 生图成功 -> 返回 URL
- **增强能力**：COS 转存 -> 提供持久化 URL

如果 COS 转存失败就让整个接口失败，会让用户误以为"图片没生成"，实际上图片已经生成完毕，只是 COS 侧出了问题。

降级方案是返回 DashScope 的临时 URL（24小时有效期），这对大多数用户场景已经足够（用户会立即看到图片并使用）。

**代价**：如果用户 24 小时后重新查看，图片链接会失效。长期建议：增加异步转存任务或告知用户及时保存。

---

### Q5：如何防止模型返回格式不稳定影响业务？

**回答要点**：

当前三层防御：

1. **提示词强约束**：prompt 末尾明确要求"严格以 JSON 格式返回"并给出示例格式，大幅降低格式漂移概率

2. **双层解析容错**：
   - 第一层：Jackson 直接解析（纯 JSON 场景）
   - 第二层：正则提取 JSON 块（模型在 JSON 前后加了解释文字的场景）

3. **字段级默认值兜底**：任何字段解析失败都有默认值（如 `auditStatus=0`），保证不会因为 NPE 或字段缺失导致主链路崩溃

**后续建议**：对解析失败的响应落库，建立测试集，通过回归测试验证 prompt 迭代后格式稳定性。

## 13. 关键代码索引

- 启动类：`novel-ai/novel-ai-service/src/main/java/com/novel/ai/NovelAiApplication.java`
- AI Bean 配置：`novel-ai/novel-ai-service/src/main/java/com/novel/ai/config/AiConfig.java`
- 前台控制器：`novel-ai/novel-ai-service/src/main/java/com/novel/ai/controller/front/FrontAiController.java`
- 内部控制器：`novel-ai/novel-ai-service/src/main/java/com/novel/ai/controller/inner/InnerAiController.java`
- 调试控制器：`novel-ai/novel-ai-service/src/main/java/com/novel/ai/controller/front/ChatController.java`
- 文本能力实现：`novel-ai/novel-ai-service/src/main/java/com/novel/ai/service/impl/TextServiceImpl.java`
- 生图实现：`novel-ai/novel-ai-service/src/main/java/com/novel/ai/service/impl/ImageServiceImpl.java`
- 书籍审核请求消费者：`novel-ai/novel-ai-service/src/main/java/com/novel/ai/mq/BookAuditRequestListener.java`
- 章节审核请求消费者：`novel-ai/novel-ai-service/src/main/java/com/novel/ai/mq/ChapterAuditRequestListener.java`
- Feign 协议：`novel-ai/novel-ai-api/src/main/java/com/novel/ai/feign/AiFeign.java`
