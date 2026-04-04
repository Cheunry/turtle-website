# 快速开始（初稿）

本章节目标：让你在最短时间内回忆并启动项目关键服务。

## 1. 环境要求

- JDK 21
- Maven 3.9+
- Docker / Docker Compose（用于容器化部署）
- MySQL、Redis、Nacos、RocketMQ、Elasticsearch（可用远端或本地）

## 2. 克隆与构建

```bash
git clone <your-repo-url>
cd turtle-website
mvn clean package -DskipTests
```

## 3. 配置准备

项目主要通过 Nacos 管理配置，建议按环境准备：

- `dev`：本地开发配置
- `prod`：生产配置（容器部署）

至少确认以下配置已就绪：

- 数据源（MySQL）
- 缓存（Redis）
- 消息队列（RocketMQ）
- 搜索（Elasticsearch）
- AI Key（如启用向量模型）

> 说明：本仓库未提交完整业务配置文件，默认依赖外部配置中心。

## 4. 本地调试建议启动顺序

1. `novel-gateway`
2. `novel-user-service`
3. `novel-book-service`
4. `novel-search-service`
5. `novel-ai-service`（按需）

## 5. 容器化部署（推荐）

```bash
# 部署前端 + 后端（默认）
./deploy.sh

# 只部署后端
./deploy.sh --backend

# 只部署前端
./deploy.sh --frontend
```

更多部署细节见：`docs/deployment.md`

## 6. 启动后验证

- 检查容器状态：`docker compose -f docker-compose.prod.yml ps`
- 检查网关端口是否可访问（默认映射 `8080`）
- 检查 Nacos 中服务注册状态
- 检查日志中是否存在 Nacos/MQ/ES 连接错误
