# SkyWalking 监控 AI API 调用指南

## 一、概述

本文档说明如何通过 SkyWalking 监控本地代码调用 AI API（DashScope）时的**实时耗时**和**异常**。

### 监控目标
- ✅ **实时耗时**：每次 AI API 调用的响应时间
- ✅ **异常监控**：调用失败、超时、限流等异常情况
- ✅ **调用链路**：完整的请求链路追踪（从 Controller → Service → AI API）
- ✅ **性能分析**：识别慢请求和瓶颈

---

## 二、自动监控（无需代码修改）

### 2.1 Spring AI 底层 HTTP 客户端

Spring AI 框架（DashScope）底层使用 **HTTP 客户端**（如 `RestTemplate`、`WebClient` 或 `OkHttp`）来调用外部 API。

**SkyWalking Java Agent 会自动监控以下 HTTP 客户端：**
- ✅ `RestTemplate`（默认支持）
- ✅ `WebClient`（需要启用插件）
- ✅ `OkHttp`（需要启用插件）
- ✅ `HttpClient`（需要启用插件）

### 2.2 自动监控范围

当你的代码调用 `chatClient.call()` 或 `imageModel.call()` 时，SkyWalking 会自动：
1. **创建 Span**：为每次 HTTP 请求创建追踪 Span
2. **记录耗时**：自动记录请求开始到响应结束的时间
3. **捕获异常**：自动捕获并记录 HTTP 异常、超时等
4. **记录标签**：HTTP 方法、URL、状态码等

### 2.3 查看监控数据

#### 步骤 1：启动服务并产生流量
```bash
# 1. 确保 SkyWalking Agent 已配置（参考 skywalking-deployment-guide.md）
# 2. 启动 novel-ai-service
# 3. 调用 AI 接口（如书籍审核、文本润色、图片生成）
```

#### 步骤 2：访问 SkyWalking UI
```
http://<服务器IP>:52584
```

#### 步骤 3：查看追踪数据
1. **拓扑图**：查看服务节点 `novel-ai-service-local`
2. **追踪（Trace）**：
   - 点击左侧菜单 `追踪（Trace）`
   - 选择服务：`novel-ai-service-local`
   - 查看最近的调用记录
3. **查看详情**：
   - 点击任意 Trace，查看完整的调用链路
   - 找到 HTTP 请求 Span（通常显示为 `GET /v1/...` 或 `POST /v1/...`）
   - **耗时**：在 Span 详情中查看 `duration`（单位：毫秒）
   - **异常**：如果调用失败，Span 会标记为红色，并显示异常信息

#### 步骤 4：性能分析
- **慢请求**：在 Trace 列表中按耗时排序，找出最慢的请求
- **异常率**：查看服务概览中的错误率统计
- **P99/P95 延迟**：查看服务的性能指标

---

## 三、增强监控（自定义 Span）

如果自动监控的粒度不够，可以通过 **SkyWalking 手动 API** 创建自定义 Span，实现更细粒度的监控。

### 3.1 添加依赖

在 `novel-ai-service/pom.xml` 中添加 SkyWalking 工具包依赖：

```xml
<dependency>
    <groupId>org.apache.skywalking</groupId>
    <artifactId>apm-toolkit-trace</artifactId>
    <version>9.2.0</version>
</dependency>
```

**注意**：版本应与 Agent 版本一致（参考 `skywalking-deployment-guide.md` 中的 Agent 版本）。

### 3.2 代码示例：监控 AI 文本调用

修改 `TextServiceImpl.java`，添加自定义 Span：

```java
package com.novel.ai.service.impl;

import org.apache.skywalking.apm.toolkit.trace.*;
import org.springframework.ai.chat.client.ChatClient;
// ... 其他导入

@Slf4j
@Service
@RequiredArgsConstructor
public class TextServiceImpl implements TextService {

    private final ChatClient chatClient;

    /**
     * AI审核书籍（带 SkyWalking 监控）
     */
    @Override
    @Trace(operationName = "AI审核书籍")
    @Tag(key = "bookId", value = "arg[0].id")
    @Tag(key = "bookName", value = "arg[0].bookName")
    public RestResp<BookAuditRespDto> auditBook(BookAuditReqDto reqDto) {
        try {
            // 构建审核提示词
            String prompt = buildAuditPrompt(reqDto.getBookName(), reqDto.getBookDesc());

            // 【关键】创建自定义 Span 监控 AI API 调用
            ActiveSpan.tag("ai.model", "qwen3-max");
            ActiveSpan.tag("ai.operation", "audit_book");
            ActiveSpan.tag("prompt.length", String.valueOf(prompt.length()));
            
            long startTime = System.currentTimeMillis();
            
            try {
                // 调用AI模型进行审核
                String aiResponse = chatClient.prompt()
                        .user(prompt)
                        .call()
                        .content();

                long duration = System.currentTimeMillis() - startTime;
                ActiveSpan.tag("ai.response.length", String.valueOf(aiResponse.length()));
                ActiveSpan.tag("ai.duration.ms", String.valueOf(duration));
                
                log.info("AI审核响应，书籍ID: {}, 耗时: {}ms, 响应: {}", 
                    reqDto.getId(), duration, aiResponse);

                // 解析AI响应
                BookAuditRespDto result = parseAuditResponse(aiResponse, reqDto.getId());
                
                // 标记成功
                ActiveSpan.tag("ai.status", "success");
                
                return RestResp.ok(result);

            } catch (Exception aiException) {
                long duration = System.currentTimeMillis() - startTime;
                ActiveSpan.tag("ai.duration.ms", String.valueOf(duration));
                ActiveSpan.tag("ai.status", "error");
                ActiveSpan.tag("ai.error.type", aiException.getClass().getSimpleName());
                ActiveSpan.tag("ai.error.message", 
                    aiException.getMessage() != null ? 
                    aiException.getMessage().substring(0, Math.min(200, aiException.getMessage().length())) : 
                    "unknown");
                
                // 记录异常到 SkyWalking
                ActiveSpan.error(aiException);
                
                // 继续原有的异常处理逻辑...
                if (isContentInspectionFailed(aiException)) {
                    // ...
                }
                // ...
            }
            
        } catch (Exception e) {
            // 记录顶层异常
            ActiveSpan.error(e);
            log.error("AI审核异常，书籍ID: {}", reqDto.getId(), e);
            // ... 原有异常处理
        }
    }

    /**
     * AI审核章节（带 SkyWalking 监控）
     */
    @Override
    @Trace(operationName = "AI审核章节")
    @Tag(key = "bookId", value = "arg[0].bookId")
    @Tag(key = "chapterNum", value = "arg[0].chapterNum")
    public RestResp<ChapterAuditRespDto> auditChapter(ChapterAuditReqDto reqDto) {
        // 类似的监控代码...
    }

    /**
     * 润色文本（带 SkyWalking 监控）
     */
    @Override
    @Trace(operationName = "AI润色文本")
    public RestResp<TextPolishRespDto> polishText(TextPolishReqDto reqDto) {
        ActiveSpan.tag("ai.model", "qwen3-max");
        ActiveSpan.tag("ai.operation", "polish_text");
        
        long startTime = System.currentTimeMillis();
        try {
            String prompt = buildPolishPrompt(reqDto);
            ActiveSpan.tag("prompt.length", String.valueOf(prompt.length()));
            
            String aiResponse = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            long duration = System.currentTimeMillis() - startTime;
            ActiveSpan.tag("ai.duration.ms", String.valueOf(duration));
            ActiveSpan.tag("ai.status", "success");
            
            log.info("AI润色响应，耗时: {}ms", duration);
            
            TextPolishRespDto result = parsePolishResponse(aiResponse);
            return RestResp.ok(result);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            ActiveSpan.tag("ai.duration.ms", String.valueOf(duration));
            ActiveSpan.tag("ai.status", "error");
            ActiveSpan.error(e);
            
            log.error("AI润色异常", e);
            return RestResp.fail(ErrorCodeEnum.SYSTEM_ERROR, "AI润色服务暂时不可用，请稍后再试");
        }
    }
    
    // ... 其他方法
}
```

### 3.3 代码示例：监控 AI 图片生成

修改 `ImageServiceImpl.java`：

```java
package com.novel.ai.service.impl;

import org.apache.skywalking.apm.toolkit.trace.*;
import org.springframework.ai.image.ImageModel;
// ... 其他导入

@Slf4j
@Service
public class ImageServiceImpl implements ImageService {

    @Resource
    private ImageModel imageModel;

    /**
     * 生成图片（带 SkyWalking 监控）
     */
    @Override
    @Trace(operationName = "AI生成图片")
    @Tag(key = "prompt.length", value = "arg[0].length()")
    @Retryable(retryFor = {Exception.class}, maxAttempts = 15,
            backoff = @Backoff(delay = 3000, multiplier = 1.5, maxDelay = 10000))
    public RestResp<String> generateImage(String prompt) {
        ActiveSpan.tag("ai.model", "qwen-image-plus");
        ActiveSpan.tag("ai.operation", "generate_image");
        ActiveSpan.tag("image.size", "1140x1472");
        
        long startTime = System.currentTimeMillis();
        int retryCount = 0; // 可以通过 ThreadLocal 或其他方式跟踪重试次数
        
        try {
            log.info("开始调用AI生图，prompt前50字: {}", prompt);
            
            ImagePrompt imagePrompt = new ImagePrompt(prompt,
                    DashScopeImageOptions.builder()
                            .withWidth(1140)
                            .withHeight(1472)
                            .withN(1)
                            .build());

            ImageResponse response = imageModel.call(imagePrompt);
            
            if (response == null || response.getResult() == null || 
                response.getResult().getOutput() == null) {
                ActiveSpan.tag("ai.status", "pending_or_failed");
                ActiveSpan.tag("ai.error", "response_is_null");
                throw new RuntimeException("AI生图结果为空");
            }
            
            String url = response.getResult().getOutput().getUrl();
            long duration = System.currentTimeMillis() - startTime;
            
            ActiveSpan.tag("ai.duration.ms", String.valueOf(duration));
            ActiveSpan.tag("ai.status", "success");
            ActiveSpan.tag("ai.image.url", url != null ? "generated" : "null");
            
            log.info("图片生成成功，耗时: {}ms, URL: {}", duration, url);
            return RestResp.ok(url);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            ActiveSpan.tag("ai.duration.ms", String.valueOf(duration));
            ActiveSpan.tag("ai.status", "error");
            ActiveSpan.tag("ai.error.type", e.getClass().getSimpleName());
            ActiveSpan.tag("ai.error.message", 
                e.getMessage() != null ? 
                e.getMessage().substring(0, Math.min(200, e.getMessage().length())) : 
                "unknown");
            
            // 记录异常（重试时会多次记录，这是正常的）
            ActiveSpan.error(e);
            
            log.warn("AI生图调用异常，耗时: {}ms, 准备重试. 异常信息: {}", duration, e.getMessage());
            throw e; // 继续抛出，触发重试
        }
    }
}
```

### 3.4 注解说明

| 注解 | 说明 | 示例 |
|------|------|------|
| `@Trace` | 标记方法为追踪点，创建新的 Span | `@Trace(operationName = "AI审核书籍")` |
| `@Tag` | 在方法入口自动添加标签 | `@Tag(key = "bookId", value = "arg[0].id")` |
| `ActiveSpan.tag()` | 手动添加标签到当前 Span | `ActiveSpan.tag("ai.model", "qwen3-max")` |
| `ActiveSpan.error()` | 记录异常到当前 Span | `ActiveSpan.error(exception)` |

---

## 四、监控指标说明

### 4.1 在 SkyWalking UI 中查看的指标

#### Trace 详情中的标签（Tags）
- `ai.model`: AI 模型名称（如 `qwen3-max`、`qwen-image-plus`）
- `ai.operation`: 操作类型（如 `audit_book`、`polish_text`、`generate_image`）
- `ai.duration.ms`: 本次调用的耗时（毫秒）
- `ai.status`: 调用状态（`success`、`error`、`pending_or_failed`）
- `ai.error.type`: 异常类型（如 `RuntimeException`、`TimeoutException`）
- `ai.error.message`: 异常消息（截取前 200 字符）
- `prompt.length`: 提示词长度
- `ai.response.length`: 响应内容长度

#### 性能指标
- **Duration（耗时）**：每次调用的总耗时
- **Status Code**：HTTP 状态码（200、429、500 等）
- **Error Rate**：错误率统计

### 4.2 常见异常类型

| 异常类型 | 可能原因 | 处理建议 |
|---------|---------|---------|
| `TimeoutException` | 请求超时 | 检查网络、增加超时时间 |
| `ThrottlingException` | 触发限流 | 实现重试机制（已有） |
| `DataInspectionFailed` | 内容安全检查失败 | 提示用户修改内容 |
| `NonTransientAiException` | 非临时性错误 | 记录日志，返回错误 |

---

## 五、最佳实践

### 5.1 监控粒度建议

- **自动监控**：适用于大多数场景，无需代码修改
- **自定义 Span**：适用于需要详细业务标签的场景（如区分不同的 AI 操作类型）

### 5.2 标签命名规范

- 使用小写字母和点号：`ai.model`、`ai.operation`
- 避免敏感信息：不要记录 API Key、完整提示词内容
- 控制标签长度：异常消息截取前 200 字符

### 5.3 性能考虑

- `ActiveSpan.tag()` 和 `ActiveSpan.error()` 是轻量级操作，对性能影响很小
- 避免在高频调用的循环中创建大量 Span

### 5.4 异常处理

- 始终使用 `ActiveSpan.error()` 记录异常，确保 SkyWalking 能捕获到
- 在 `catch` 块中记录异常，不要吞掉异常

---

## 六、验证步骤

### 6.1 验证自动监控

1. **启动服务**（确保已配置 SkyWalking Agent）
2. **调用 AI 接口**（如书籍审核）
3. **查看 SkyWalking UI**：
   - 进入 `追踪（Trace）` 页面
   - 选择服务 `novel-ai-service-local`
   - 找到最近的 Trace，查看是否有 HTTP 请求 Span
   - 检查 Span 的耗时和状态

### 6.2 验证自定义监控

1. **添加依赖**（`apm-toolkit-trace`）
2. **添加代码**（`@Trace`、`ActiveSpan.tag()` 等）
3. **重启服务**
4. **调用 AI 接口**
5. **查看 SkyWalking UI**：
   - 在 Trace 详情中，应该能看到自定义的 `operationName`（如 "AI审核书籍"）
   - 在 Span 的 Tags 中，应该能看到自定义标签（如 `ai.model`、`ai.duration.ms`）

---

## 七、常见问题

### Q1: 为什么看不到 AI API 调用的 Span？

**可能原因**：
1. SkyWalking Agent 未正确配置
2. HTTP 客户端插件未启用（如 WebClient）
3. 没有产生实际流量

**解决方案**：
1. 检查 Agent 日志：`/Users/cheunry/Environment/skywalking/skywalking-agent/logs/skywalking-api.log`
2. 确认 Agent 版本支持 Spring Boot 3.x（推荐 9.2.0+）
3. 调用接口后等待 10-30 秒再查看 UI

### Q2: 如何区分不同的 AI 操作？

**解决方案**：使用自定义 Span 和标签
- 使用 `@Trace(operationName = "...")` 设置不同的操作名称
- 使用 `ActiveSpan.tag("ai.operation", "...")` 添加业务标签

### Q3: 如何监控重试次数？

**解决方案**：在重试逻辑中记录标签
```java
// 使用 ThreadLocal 或其他方式跟踪重试次数
private static final ThreadLocal<Integer> retryCount = new ThreadLocal<>();

// 在重试前
retryCount.set(retryCount.get() == null ? 1 : retryCount.get() + 1);
ActiveSpan.tag("ai.retry.count", String.valueOf(retryCount.get()));
```

### Q4: 监控数据延迟多久显示？

**答案**：通常 10-30 秒。SkyWalking Agent 会批量上报数据，不是实时的。

---

## 八、总结

### 快速开始
1. ✅ **自动监控**：配置 SkyWalking Agent 后即可自动监控 HTTP 请求
2. ✅ **增强监控**：添加 `apm-toolkit-trace` 依赖，使用 `@Trace` 和 `ActiveSpan` API

### 关键要点
- SkyWalking 会自动监控 Spring AI 底层的 HTTP 调用
- 自定义 Span 可以提供更细粒度的业务监控
- 在 SkyWalking UI 中可以查看实时耗时和异常信息

---

**文档版本**: v1.0  
**最后更新**: 2025-12-24  
**适用环境**: Spring Boot 3.x + Spring AI + SkyWalking 9.2.0+

