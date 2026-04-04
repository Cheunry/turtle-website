# Turtle Website（微服务小说平台）

这是一个基于 Spring Cloud 的微服务小说平台后端工程，包含网关、用户、书籍、搜索、AI 等核心能力。  
文档目标是帮助自己快速回忆设计细节，同时支持面试场景下的架构讲解、技术选型说明、问题排查与优化复盘。

## 1. 项目定位

- **项目类型**：微服务架构的小说平台后端
- **核心价值**：提供用户体系、作者创作、书籍管理、搜索检索、AI审核等能力
- **文档受众**：自己（维护/迭代）+ 面试官（架构表达）

## 2. 技术栈

- **基础框架**：Java 21、Spring Boot 3.4.3、Spring Cloud 2024.0.0
- **服务治理**：Spring Cloud Alibaba、Nacos（配置中心 + 服务发现）
- **网关与鉴权**：Spring Cloud Gateway、JWT（统一鉴权）
- **数据与缓存**：MySQL、MyBatis-Plus、Redis/Redisson
- **消息与异步**：RocketMQ
- **搜索与AI**：Elasticsearch 8.10.4、Spring AI Alibaba（DashScope Embedding）
- **调度与观测**：XXL-JOB、SkyWalking
- **部署方式**：Docker + Docker Compose + `deploy.sh`

## 3. 模块结构

```text
turtle-website
├── novel-core                  # 公共能力（common/config）
├── novel-gateway               # 网关服务（统一入口）
├── novel-user                  # 用户/作者服务
├── novel-book                  # 书籍与章节服务
├── novel-search                # 搜索服务（ES + 向量检索）
├── novel-ai                    # AI能力服务（审核/生成相关）
├── docker-compose.prod.yml     # 生产编排
├── deploy.sh                   # 一键部署脚本
├── docs                        # 当前主文档目录（总览 + 快速上手 + 部署 + 排障）
└── doc                         # 历史沉淀文档（专题深度材料）
```

## 4. 快速开始

建议先看：`docs/quick-start.md`  
如果是生产部署：`docs/deployment.md`

常用命令（项目根目录）：

```bash
# 部署前端 + 后端（默认）
./deploy.sh

# 只部署后端
./deploy.sh --backend

# 只部署前端
./deploy.sh --frontend
```

## 5. 文档导航

- **整体架构与设计说明**：`docs/architecture.md`
- **网关模块面试版**：`docs/gateway-module.md`
- **用户服务模块面试版**：`docs/user-module.md`
- **书籍服务模块面试版**：`docs/book-module.md`
- **搜索服务模块面试版**：`docs/search-module.md`
- **AI 服务模块面试版**：`docs/ai-module.md`
- **本地启动与调试路径**：`docs/quick-start.md`
- **部署与运维手册**：`docs/deployment.md`
- **接口文档策略（初稿）**：`docs/api.md`
- **常见问题与排查**：`docs/faq.md`
- **版本变更记录**：`CHANGELOG.md`

## 6. 已有专题资料（建议面试前重点复习）

- 搜索系统完整设计：`doc/es/search_system_complete_guide.md`
- 性能优化复盘：`doc/note/性能优化总结文档.md`
- Nacos 排障指南：`doc/note/NACOS_TROUBLESHOOTING.md`
- 面试问答沉淀：`doc/question/`

## 7. 下一步完善建议

1. 先完善 `docs/architecture.md` 中每个模块的“职责 + 关键链路 + 取舍点”
2. 再补 `docs/api.md`：整理高频业务接口与鉴权规则
3. 最后补 `docs/faq.md`：把线上故障按“现象-定位-解决-预防”结构沉淀