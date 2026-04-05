# Nacos 本地与生产环境配置说明

本文档归纳仓库中为区分**本地开发**与**线上 Docker 部署**而对 Nacos 相关配置所做的调整，以及使用时的注意点。

## 背景与目标

- 本地连 MySQL、ES、MQ 等方式与服务器不一致，若反复修改 Nacos 同一套配置，容易出错且繁琐。
- 目标：用 **Nacos 命名空间** 隔离「本地配置」与「生产配置」，用 **环境变量** 指定 Nacos 地址与命名空间；**打镜像无需特殊参数**，运行时由 `docker-compose` 注入环境变量。

## Nacos 控制台侧（需自行完成）

1. 创建两个命名空间（示例）：
   - 显示名 `local`，**命名空间 ID**：`local_namespace`
   - 显示名 `prod`，**命名空间 ID**：`prod_namespace`
2. 分别在两个命名空间中导入相同的 Data ID / Group（如 `DEFAULT_GROUP`），仅修改与运行环境相关的项（数据库、Redis、RocketMQ、ES 等）。
3. 生产命名空间中的中间件地址应使用**内网**可达的主机名或 IP。

## 服务器上 Nacos 容器（参考）

示例：`nacos/nacos-server:v2.2.1`，容器名 `novel-nacos-server`。

| 含义           | 宿主机端口 | 容器内端口 |
|----------------|------------|------------|
| HTTP / 控制台等 | 52582      | 8848       |
| Nacos 2.x gRPC | 53582      | 9848       |

说明：

- **本机或外网**访问 Nacos 时，可使用 `服务器IP:52582`（对应容器内 8848）。Nacos 2.x Java 客户端默认按「主端口 + 1000」推导 gRPC，即 `52582 + 1000 = 53582`，与 `53582 → 9848` 的映射一致。
- **业务服务与其它容器在同一 Docker 网络内**时，应使用 **`novel-nacos-server:8848`**（容器内端口），**不要**写成宿主机 `52582`（其它容器无法按宿主机映射语义访问）。

业务容器与 Nacos 必须在同一网络（如 `common-network`）。若 Nacos 未加入该网络，需在宿主机执行例如：

```bash
docker network connect common-network novel-nacos-server
```

## 代码与仓库中的改动摘要

### 1. `bootstrap.yml`（各微服务 + `novel-config` 的 `bootstrap-common.yml`）

- **`spring.cloud.nacos.config.namespace`** 与 **`spring.cloud.nacos.discovery.namespace`**：  
  `namespace: ${NACOS_NAMESPACE_ID:local_namespace}`  
  未设置环境变量时默认走本地命名空间；生产由 compose 注入 `prod_namespace`。
- **`group`**：显式为 `DEFAULT_GROUP`（与 Nacos 中一致即可）。
- **`spring.cloud.nacos.server-addr`**：  
  `server-addr: ${NACOS_SERVER_ADDR:120.53.89.103:52582}`（默认值可按实际服务器调整）。  
  `config` / `discovery` 的 `server-addr` 引用 `${spring.cloud.nacos.server-addr}`，与网关写法统一。

### 2. `application-common.yml`

- `spring.cloud.nacos.discovery.server-addr` 同样使用 `${NACOS_SERVER_ADDR:...}`，与 bootstrap 行为一致。

### 3. `docker-compose.prod.yml`

- 文件头注释说明：宿主机端口映射、容器内应使用 `novel-nacos-server:8848`、gRPC 与 +1000 规则。
- 使用 YAML 锚点 `x-nacos-client-env` 统一各 Java 服务环境变量，避免重复与遗漏：
  - `SPRING_PROFILES_ACTIVE=prod`
  - `NACOS_SERVER_ADDR=novel-nacos-server:8848`
  - `NACOS_NAMESPACE_ID=prod_namespace`
  - `TZ=Asia/Shanghai`

## 本地运行（IDE / 命令行）

- 一般**无需**设置 `NACOS_NAMESPACE_ID`、`NACOS_SERVER_ADDR`：默认 `local_namespace` + 默认 Nacos 地址。
- 确保 Nacos 中 **`local_namespace`** 已配置且与本地中间件一致。
- `bootstrap` 中多为 `spring.profiles.active: dev`，需与 Nacos 中实际 Data ID（如 `*-dev.yml`）一致。
- 若 Nacos 开启鉴权，需在 `bootstrap.yml` 或通过环境变量配置用户名密码。

## 打包镜像与线上启动

- **构建镜像**：照常 Maven / Docker 构建，**不必**为 Nacos 单独传构建参数。
- **运行**：使用本仓库的 `docker-compose.prod.yml`（或等价环境变量）即可：
  - 内网访问 Nacos：`NACOS_SERVER_ADDR=novel-nacos-server:8848`
  - 生产配置空间：`NACOS_NAMESPACE_ID=prod_namespace`
  - 激活生产 Profile：`SPRING_PROFILES_ACTIVE=prod`
- 确认 **`prod_namespace`** 内配置为内网中间件；确认 Nacos 与业务服务在同一 Docker 网络。

## 常见注意点

1. 本地调试时**不要**长期设置 `NACOS_NAMESPACE_ID=prod_namespace`，避免误用生产配置。
2. 若服务器公网 IP 或 Nacos 映射端口变更，需同步修改各 `bootstrap` 与 `application-common.yml` 中的**默认值**，或全员通过环境变量统一覆盖。
3. 线上使用 `prod` Profile 时，Nacos 中需存在对应的 `novel-*-prod.yml`（或项目实际约定的 Data ID）。
4. `docker-compose.prod.yml` 中的 SkyWalking 挂载路径在本地直接跑 jar 时通常不存在，本地开发不必使用该 entrypoint。

## 相关文件路径（便于检索）

| 文件 |
|------|
| `docker-compose.prod.yml` |
| `novel-gateway/src/main/resources/bootstrap.yml` |
| `novel-user/novel-user-service/src/main/resources/bootstrap.yml` |
| `novel-book/novel-book-service/src/main/resources/bootstrap.yml` |
| `novel-search/src/main/resources/bootstrap.yml` |
| `novel-ai/novel-ai-service/src/main/resources/bootstrap.yml` |
| `novel-core/novel-config/src/main/resources/bootstrap-common.yml` |
| `novel-core/novel-config/src/main/resources/application-common.yml` |

其它 Nacos 排障与网关配置可参考同目录下的 `NACOS_TROUBLESHOOTING.md`、`NACOS_CONFIG_UPDATE.md` 等文档。
