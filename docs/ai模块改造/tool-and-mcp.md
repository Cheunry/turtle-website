# Tool Calling 与 MCP Server（novel-ai-service）

本文说明 `novel-ai-service` 中 **Spring AI `@Tool`（Function Calling）** 与 **MCP（Model Context Protocol）HTTP/SSE 服务** 的用法，便于本地联调与 Cursor / Claude Desktop 接入。

**与其它文档**：审核流水线、按类别/学习资料是否挂 RAG 等见同目录 [审核智能体-当前实现与改动.md](./审核智能体-当前实现与改动.md)；索引见 [README.md](./README.md)。

## 1. 架构关系

| 组件 | 作用 |
|------|------|
| `AuditTools` | 带 `@Tool` 注解的方法集合（政策、向量判例、敏感词、人审 MQ、判例入库等）。 |
| `AiConfig#chatClient` | `ChatClient.defaultTools(auditTools)`，审核/润色等 LLM 调用链上模型可 **ReAct 循环** 调工具。 |
| `McpToolRegistrationConfig` | `MethodToolCallbackProvider.builder().toolObjects(auditTools)`，向 MCP Server **注册同一套工具**。 |
| `spring-ai-starter-mcp-server-webmvc` | 在 **9070** 端口提供 MCP 传输（与现有 Spring MVC 共存）。 |

## 2. 默认 HTTP 端点（Spring AI 1.0）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/sse` | SSE 连接，MCP 客户端建立会话。 |
| POST | `/mcp/message` | JSON-RPC 消息（与 SSE 配合使用）。 |

配置项前缀：`spring.ai.mcp.server.*`，见 `application.properties` 中「Spring AI MCP Server」段落。

若经网关或反向代理暴露，请设置 `spring.ai.mcp.server.base-url` 为对外可访问的基地址（含协议与端口），避免客户端拼接错误。

## 3. Nacos：审核政策（Tool `queryPlatformPolicy`）

- **dataId**：`novel-ai-policies.yml`  
- **group**：`DEFAULT_GROUP`  
- **内容**：`novel.ai.policy.rules` 列表（见仓库 `nacos/local/DEFAULT_GROUP/novel-ai-policies.yml` 示例）。

本地与生产分别对应：

- 本地开发：`nacos/local/DEFAULT_GROUP/novel-ai-policies.yml` → 上传到 **本地测试用** 命名空间。  
- 服务器：`nacos/prod/DEFAULT_GROUP/novel-ai-policies.yml` → 上传到 **Docker/生产** 命名空间。

`bootstrap.yml` 已通过 `extension-configs` 拉取该 dataId；`NovelAiPolicyProperties` 带 `@RefreshScope`，控制台修改后可热更新。

## 4. RocketMQ：人审工单（Tool `escalateToHuman`）

- **Topic**：`topic-ai-human-review-task`（定义见 `AiMqConsts.HumanReviewTaskMq`）。  
- **Tag**：`from_book` / `from_chapter` / `from_agent_tool`。

**请在 RocketMQ 控制台创建该 Topic**，否则消息发送会失败（Producer 已做异常吞没 + 日志，不阻断审核主流程）。

消费方（人审后台）可后续接入。

## 5. 怎么演示（Cursor 推荐）

### 5.1 演示前检查

1. **启动** `novel-ai-service`（默认端口 `9070`）。  
2. **Nacos** 已上传 `novel-ai-policies.yml`，否则 `queryPlatformPolicy` 返回空列表。  
3. **浏览器** 打开 `http://127.0.0.1:9070/sse`，应看到类似 `event: endpoint` 与 `data: /mcp/message?sessionId=...`（说明 SSE 正常）。  
4. （可选）**RocketMQ** 已创建 Topic `topic-ai-human-review-task`，否则 `escalateToHuman` 可能只打日志、工单投递失败。

### 5.2 在 Cursor 里挂上 MCP

1. 在**项目根目录**（或 `turtle-website` 根目录）创建 `.cursor/mcp.json`（没有 `.cursor` 文件夹就先建一个）。  
2. 写入（**无鉴权**时的最小配置；`url` 填 **SSE 完整地址**）：

```json
{
  "mcpServers": {
    "novel-ai-audit": {
      "url": "http://127.0.0.1:9070/sse"
    }
  }
}
```

3. **完全退出 Cursor 再打开**（MCP 多在启动时加载）。  
4. **确认 MCP 已加载**（任选一种方式，不同版本菜单名可能不同）：
   - **设置里搜索**：`Cmd + ,`（Mac）或 `Ctrl + ,`（Windows）打开 Settings，在顶部搜索框输入 **`MCP`** 或 **`Model Context Protocol`**，进入对应页面，看是否列出 `novel-ai-audit`。  
   - **官方文档路径**（部分版本）：`Cmd + Shift + J`（Mac）打开 Cursor Settings → **Features** → **Model Context Protocol**；若找不到 **Features**，用上面「搜索 MCP」即可。  
   - **命令面板**：`Cmd + Shift + P`（Mac）或 `Ctrl + Shift + P`（Windows），输入 **`MCP`** 看是否有「打开 MCP 设置」类命令。  
   - **不依赖 UI**：只要 `.cursor/mcp.json` 在**当前工作区根**且服务已启动，打开 **Agent 对话**，在输入框附近或工具区若出现 **Available Tools** / 你的工具名，即表示已连通。  
   - **排错**：菜单 **View → Output**，下拉选 **MCP Logs**，可看连接是否成功。

### 5.3 演示时说什么、点什么

在 Cursor 对话里（建议用带 Agent/工具的模型）可以这样说：

- 「列出当前可用的 MCP 工具」或 「What MCP tools do you have?」  
- 应能看到与 `AuditTools` 对应的工具名（如 `queryPlatformPolicy`、`querySimilarViolations` 等，具体以模型展示的名为准）。  
- 再试：「用 MCP 调用 queryPlatformPolicy，topic 为 violence」，应返回 Nacos 里配置的政策文案。

**面试口述模板**：「本地起 novel-ai，SSE 在 `/sse`，Cursor 用 `mcp.json` 指过去；对话里让模型列工具并调政策查询，证明 Tool 与 MCP 协议打通。」

### 5.4 其它客户端（可选）

- **Claude Desktop**：配置文件里增加 remote server，`url` 同样指向 `http://127.0.0.1:9070/sse`（路径以该版本文档为准）。  
- **不用 Cursor**：也可用任意支持 **MCP over HTTP/SSE** 的客户端，只要能连上 `/sse` 并按协议向返回的 `/mcp/message?sessionId=...` 发 JSON-RPC。

## 6. 与 Elasticsearch 8.10 的索引说明

向量索引 `novel_ai_audit_experiences` 需 **手动建 mapping**（`dense_vector` 含 `index: true`），且 `spring.ai.vectorstore.elasticsearch.initialize-schema=false`。详见 `application.properties` 内注释。

## 7. 参考类路径

- `com.novel.ai.tool.AuditTools`  
- `com.novel.ai.config.McpToolRegistrationConfig`  
- `com.novel.ai.config.AiConfig`  
- `org.springframework.ai.mcp.server.autoconfigure.McpServerProperties`（`spring.ai.mcp.server`）
