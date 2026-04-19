# AI 模块改造（文档索引）

本目录存放 **novel-ai** 相关的设计、路线图与运维说明，与代码仓库 `turtle-website/novel-ai` 对应。

| 文档 | 说明 |
|------|------|
| [改造计划.md](./改造计划.md) | 早期对 novel-ai 与「标准 Agent」差距的诊断、分阶段路线图与技术选型（部分内容已被后续实现覆盖，文首有更新说明）。 |
| [tool-and-mcp.md](./tool-and-mcp.md) | `AuditTools`、`ChatClient` 工具链、MCP Server（`/sse`）、Nacos 政策、RocketMQ 人审工单等。 |
| [审核智能体-当前实现与改动.md](./审核智能体-当前实现与改动.md) | **当前**审核流水线、按类别/学习资料策略、Nacos 配置、与数据库字段对应关系及近期改动清单（建议优先阅读）。 |

维护约定：策略与 Prompt 以 **Nacos** 与 `novel-ai-service/src/main/resources/prompts/*.st` 为准；架构以 **代码** 为准。
