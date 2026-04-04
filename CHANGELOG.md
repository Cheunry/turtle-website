# Changelog

本文件记录关键文档与架构层面的里程碑变更。

## [Unreleased]

### Added

- 新增文档目录 `docs/`，建立统一文档入口。
- 新增 `docs/architecture.md`，沉淀整体架构、技术选型、排障流程、优化概览。
- 新增 `docs/gateway-module.md`，沉淀网关模块的面试版详解。
- 新增 `docs/user-module.md`，沉淀用户服务模块的面试版详解。
- 新增 `docs/book-module.md`，沉淀书籍服务模块的面试版详解。
- 新增 `docs/search-module.md`，沉淀搜索服务模块的面试版详解。
- 新增 `docs/ai-module.md`，沉淀 AI 服务模块的面试版详解，含异常矩阵、提示词设计、重试参数、模型选型对比与面试追问详细回答。
- 新增 `docs/quick-start.md`，沉淀环境要求与启动路径。
- 新增 `docs/deployment.md`，沉淀基于 `deploy.sh` 的部署与运维流程。
- 新增 `docs/api.md`，定义接口文档结构与编写模板。
- 新增 `docs/faq.md`，建立常见问题排查清单。

### Changed

- 重写 `README.md` 作为项目总入口，统一链接到 `docs/` 与历史专题文档 `doc/`。
- 修正 `doc/es/search_system_complete_guide.md` 中的脏字符与错误命令示例。
