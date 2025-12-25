# SkyWalking 从 0 到 1 部署使用指南

## 目录
- [一、概述](#一概述)
- [二、版本选择](#二版本选择)
- [三、服务器端部署（Docker Compose）](#三服务器端部署docker-compose)
- [四、本地开发环境配置（IDEA）](#四本地开发环境配置idea)
- [五、Spring Cloud Gateway 特殊配置](#五spring-cloud-gateway-特殊配置)
- [六、验证与排查](#六验证与排查)
- [七、常见问题](#七常见问题)

---

## 一、概述

### 什么是 SkyWalking？
Apache SkyWalking 是一个应用性能监控（APM）系统，用于分布式系统的链路追踪、性能指标收集和可视化。

### 架构组件
- **BanyanDB**: 轻量级时序数据库，用于存储监控数据（比 Elasticsearch 更省资源）
- **OAP Server**: 后端服务，接收 Agent 上报的数据并处理
- **UI**: Web 前端界面，用于查看监控数据
- **Java Agent**: 探针，嵌入到 Java 应用中，自动采集数据

### 部署架构
```
本地 IDEA (Java 应用 + Agent) 
    ↓ (gRPC:11800)
服务器 Docker (OAP Server)
    ↓ (内部网络)
服务器 Docker (BanyanDB)
```

---

## 二、版本选择

### 核心原则：版本必须匹配

| 组件 | 推荐版本 | 说明 |
|------|---------|------|
| **OAP Server** | `9.7.0` | 稳定版本，功能完整 |
| **BanyanDB** | `0.5.0` | ⚠️ **必须与 OAP 内置客户端匹配** |
| **UI** | `9.7.0` | 与 OAP 版本保持一致 |
| **Java Agent** | `9.2.0+` | 支持 Spring Boot 3.x / Gateway 4.x |

### ⚠️ 重要：BanyanDB 版本兼容性
- **OAP 9.7.0** 内置的 BanyanDB Java Client 是 `0.5.0`
- 如果使用 `BanyanDB 0.6.1`，会出现协议不兼容错误：
  ```
  INVALID_ARGUMENT: invalid IndexRule.Type: value must be one of the defined enum values
  ```
- **解决方案**：必须使用 `apache/skywalking-banyandb:0.5.0`

### Spring Boot 3.x 支持
- **Spring Boot 3.x + Gateway 4.x** 需要 Agent `9.2.0+`
- 旧版本 Agent（8.x）不支持 Spring Framework 6

---

## 三、服务器端部署（Docker Compose）

### 3.1 添加到 docker-compose.yml

将以下配置追加到 `turtle-website/doc/docker/turtle-website-compose.yml` 的 `services` 部分：

```yaml
  #------------------SkyWalking + BanyanDB----------------------#
  # 1. BanyanDB 存储后端
  novel-banyandb:
    image: apache/skywalking-banyandb:0.5.0  # ⚠️ 必须用 0.5.0
    container_name: novel-banyandb
    restart: always
    ulimits:
      nofile:
        soft: 65536
        hard: 65536
    ports:
      - "17912:17912"  # gRPC 端口（内部通信，无需对外暴露）
      - "17913:17913"  # HTTP 端口（管理用，无需对外暴露）
    environment:
      - TZ=Asia/Shanghai  # 时区设置，避免日志时间差 8 小时
    volumes:
      # 持久化数据，防止容器重启丢失历史数据
      - ../volumes/skywalking/banyandb-data:/tmp/banyandb-data
    networks:
      - common-network
    command: standalone

  # 2. SkyWalking OAP 服务端
  novel-skywalking-oap:
    image: apache/skywalking-oap-server:9.7.0
    container_name: novel-skywalking-oap
    restart: always
    depends_on:
      - novel-banyandb
    ports:
      - "11800:11800"  # 【需防火墙放行】Agent gRPC 上报端口
      - "12800:12800"  # HTTP 查询端口（UI 内部连接用，无需对外暴露）
    environment:
      - SW_STORAGE=banyandb
      - SW_STORAGE_BANYANDB_TARGETS=novel-banyandb:17912
      - SW_HEALTH_CHECKER=default
      - SW_TELEMETRY=prometheus
      - TZ=Asia/Shanghai
      # ⚠️ 内存配置：堆内存 800M < 容器限制 1024M，留出余量防止 OOMKilled
      # Java 进程 = 堆内存 + 非堆内存（Metaspace、Direct Memory 等）
      - JAVA_OPTS=-Xms512m -Xmx800m
    deploy:
      resources:
        limits:
          memory: 1024M  # Docker 容器硬限制
    networks:
      - common-network

  # 3. SkyWalking UI 界面
  novel-skywalking-ui:
    image: apache/skywalking-ui:9.7.0
    container_name: novel-skywalking-ui
    restart: always
    depends_on:
      - novel-skywalking-oap
    ports:
      - "52584:8080"  # 【需防火墙放行】Web 访问端口
    environment:
      - SW_OAP_ADDRESS=http://novel-skywalking-oap:12800
      - TZ=Asia/Shanghai
    deploy:
      resources:
        limits:
          memory: 512M
    networks:
      - common-network
```

### 3.2 关键配置说明

#### 内存限制（防止 OOM）
- **问题**：如果 `JAVA_OPTS=-Xmx1024m` 而 `Docker limit=1024M`，容器会被系统 Kill
- **原因**：Java 进程总内存 = 堆内存 + 非堆内存（约 +200M~400M）
- **解决**：`-Xmx800m` + `limit: 1024M`，留出安全余量

#### 时区设置
- **问题**：Docker 容器默认 UTC 时间，日志比北京时间慢 8 小时
- **解决**：所有服务添加 `TZ=Asia/Shanghai`

#### 端口映射策略
- **11800**: 必须对外暴露（本地代码需要连接）
- **12800**: 无需对外暴露（UI 通过 Docker 内部网络连接）
- **17912/17913**: 无需对外暴露（OAP 通过 Docker 内部网络连接）
- **52584**: 必须对外暴露（浏览器访问 UI）

### 3.3 启动服务

```bash
# 进入 docker-compose.yml 所在目录
cd ~/docker  # 或您的实际路径

# 启动 SkyWalking 相关服务
docker-compose -f turtle-website-compose.yml up -d novel-banyandb novel-skywalking-oap novel-skywalking-ui

# 查看启动状态
docker ps | grep skywalking

# 查看日志（如有问题）
docker logs --tail 50 novel-skywalking-oap
```

### 3.4 防火墙配置

#### 云服务器安全组（必须）
在腾讯云/阿里云控制台 -> 安全组 -> 添加入站规则：
- **端口**: `11800` (TCP) - Agent 数据上报
- **端口**: `52584` (TCP) - UI 访问

#### 服务器内部防火墙（Ubuntu）
```bash
# 检查 UFW 状态
sudo ufw status

# 如果开启了，需要放行端口
sudo ufw allow 11800/tcp
sudo ufw allow 52584/tcp
sudo ufw reload
```

### 3.5 验证部署

```bash
# 1. 检查容器状态（应该都是 Up）
docker ps | grep skywalking

# 2. 检查 OAP 日志（应该看到 "OAP server started"）
docker logs --tail 20 novel-skywalking-oap

# 3. 本地测试端口（应该返回 HTML）
curl http://localhost:52584

# 4. 浏览器访问
# http://<服务器IP>:52584
```

---

## 四、本地开发环境配置（IDEA）

### 4.1 下载 Java Agent

1. 访问官网：https://skywalking.apache.org/downloads/
2. 下载 **Java Agent**（推荐 `9.2.0+` 版本，支持 Spring Boot 3.x）
3. 解压到本地固定目录，例如：
   - Mac/Linux: `/Users/cheunry/Environment/skywalking/skywalking-agent/`
   - Windows: `D:\Tools\skywalking-agent\`

### 4.2 配置 IDEA VM Options

#### 步骤
1. 打开 IDEA，选择要启动的微服务（如 `novel-user-service`）
2. 点击右上角运行配置下拉框 -> `Edit Configurations...`
3. 找到 `VM options` 输入框
4. 添加以下参数（**一行，空格分隔**）：

```bash
-javaagent:/Users/cheunry/Environment/skywalking/skywalking-agent/skywalking-agent.jar -Dskywalking.agent.service_name=novel-user-local -Dskywalking.collector.backend_service=120.53.89.103:11800
```

#### 参数说明
- `-javaagent:...`: Agent Jar 包**完整路径**（必须指向 `.jar` 文件）
- `-Dskywalking.agent.service_name=...`: 服务名称（在 UI 上显示的名字）
- `-Dskywalking.collector.backend_service=...`: OAP 服务器地址（IP:端口）

#### 配置示例（不同服务）

**novel-user-service:**
```bash
-javaagent:/Users/cheunry/Environment/skywalking/skywalking-agent/skywalking-agent.jar -Dskywalking.agent.service_name=novel-user-local -Dskywalking.collector.backend_service=120.53.89.103:11800
```

**novel-book-service:**
```bash
-javaagent:/Users/cheunry/Environment/skywalking/skywalking-agent/skywalking-agent.jar -Dskywalking.agent.service_name=novel-book-local -Dskywalking.collector.backend_service=120.53.89.103:11800
```

**novel-gateway:**
```bash
-javaagent:/Users/cheunry/Environment/skywalking/skywalking-agent/skywalking-agent.jar -Dskywalking.agent.service_name=novel-gateway-local -Dskywalking.collector.backend_service=120.53.89.103:11800
```

### 4.3 启动并验证

1. 启动服务，观察控制台日志
2. 访问几个接口产生流量
3. 等待 10-30 秒
4. 刷新 SkyWalking UI，应该能看到服务出现在拓扑图中

---

## 五、Spring Cloud Gateway 特殊配置

### 5.1 问题背景

**Spring Cloud Gateway** 基于 **WebFlux（Reactor/Netty）**，是异步响应式框架。默认的 Agent 可能无法自动识别，需要手动启用插件。

### 5.2 版本对应关系

| Spring Boot | Spring Cloud Gateway | 需要的插件 |
|------------|---------------------|-----------|
| 2.x | 2.x / 3.x | `apm-spring-cloud-gateway-2.x-plugin` + `apm-spring-webflux-5.x-plugin` |
| **3.x** | **4.x** | `apm-spring-cloud-gateway-4.x-plugin` + `apm-spring-webflux-6.x-plugin` |

### 5.3 操作步骤（Spring Boot 3.x + Gateway 4.x）

#### 1. 找到 Agent 目录
```bash
/Users/cheunry/Environment/skywalking/skywalking-agent/
```

#### 2. 进入可选插件目录
```bash
cd /Users/cheunry/Environment/skywalking/skywalking-agent/optional-plugins
```

#### 3. 查找并复制插件
找到以下两个文件（版本号可能不同）：
- `apm-spring-cloud-gateway-4.x-plugin-x.x.x.jar`
- `apm-spring-webflux-6.x-plugin-x.x.x.jar`

**复制到 `plugins` 目录：**
```bash
# 在 optional-plugins 目录下执行
cp apm-spring-cloud-gateway-4.x-plugin-*.jar ../plugins/
cp apm-spring-webflux-6.x-plugin-*.jar ../plugins/
```

#### 4. 清理冲突插件（重要！）
检查 `plugins` 目录，**删除**以下旧版本插件（如果存在）：
- ❌ `apm-spring-cloud-gateway-2.x-plugin-*.jar`
- ❌ `apm-spring-cloud-gateway-3.x-plugin-*.jar`
- ❌ `apm-spring-webflux-5.x-plugin-*.jar`

**原因**：新旧插件同时存在会导致冲突，Agent 可能加载错误的插件。

#### 5. 重启 Gateway 服务
重启后，观察启动日志，应该能看到：
```
DEBUG ... Loaded plugin: [Spring Cloud Gateway 4.x plugin]
```

### 5.4 验证 Gateway 监控

1. 通过 Gateway 端口访问业务接口（例如：`http://localhost:8888/api/user/login`）
2. 等待 10-30 秒
3. 在 SkyWalking UI 的拓扑图中，应该能看到 `novel-gateway-local` 节点
4. 点击节点，查看 Trace 详情

---

## 六、验证与排查

### 6.1 部署验证清单

- [ ] 容器状态：`docker ps` 显示三个服务都是 `Up`
- [ ] OAP 日志：`docker logs novel-skywalking-oap` 无报错，显示 "started"
- [ ] 端口测试：`curl http://localhost:52584` 返回 HTML
- [ ] 浏览器访问：`http://<服务器IP>:52584` 能打开页面
- [ ] 防火墙：安全组和 UFW 都已放行 `11800` 和 `52584`

### 6.2 本地 Agent 验证

- [ ] Agent 路径正确：指向 `.jar` 文件，不是目录
- [ ] VM Options 格式正确：`-javaagent:...` 开头
- [ ] 服务名称唯一：不同服务使用不同的 `service_name`
- [ ] OAP 地址正确：IP 和端口（11800）无误
- [ ] 有流量产生：访问接口后等待 10-30 秒

### 6.3 查看 Agent 日志

Agent 运行日志位于：
```
/Users/cheunry/Environment/skywalking/skywalking-agent/logs/skywalking-api.log
```

如果连接失败，日志中会显示：
```
ERROR ... Failed to connect to backend service: ...
```

---

## 七、常见问题

### Q1: OAP 容器一直重启（CrashLoop）

**症状**：`docker ps` 显示 `Up Less than a second` 或 `Restarting`

**排查步骤**：
```bash
docker logs --tail 50 novel-skywalking-oap
```

**常见原因**：
1. **版本不兼容**：BanyanDB 版本必须是 `0.5.0`（不是 0.6.1）
2. **内存不足**：检查 `JAVA_OPTS` 和 `deploy.limits.memory` 的比例
3. **连接失败**：检查 BanyanDB 是否正常启动

### Q2: UI 页面打不开

**症状**：浏览器访问 `http://IP:52584` 显示超时或连接被拒绝

**排查步骤**：
1. 服务器本地测试：`curl http://localhost:52584`（应该返回 HTML）
2. 检查防火墙：安全组和 UFW 是否放行 `52584`
3. 检查容器状态：`docker ps | grep skywalking-ui`

### Q3: Gateway 服务没有数据

**症状**：其他服务有数据，但 Gateway 没有出现在拓扑图

**排查步骤**：
1. 确认已复制 `gateway-4.x` 和 `webflux-6.x` 插件到 `plugins` 目录
2. 确认已删除旧版本插件（2.x/3.x/5.x）
3. 确认 Agent 版本是 `9.2.0+`
4. 确认有流量通过 Gateway（访问接口）
5. 查看 Agent 日志：`logs/skywalking-api.log`

### Q4: 本地代码连接不上 OAP

**症状**：Agent 日志显示 "Failed to connect to backend service"

**排查步骤**：
1. 检查 VM Options 中的 IP 和端口是否正确
2. 检查服务器防火墙是否放行 `11800`
3. 在本地电脑执行：`telnet <服务器IP> 11800`（应该能连接）

### Q5: 时区不对（日志时间差 8 小时）

**症状**：SkyWalking UI 显示的时间比实际时间慢 8 小时

**解决**：确保所有 Docker 服务都添加了 `TZ=Asia/Shanghai`

### Q6: 容器被 OOMKilled

**症状**：`docker inspect novel-skywalking-oap` 显示 `ExitCode: 137`

**原因**：容器内存超出限制

**解决**：
- 调整 `JAVA_OPTS=-Xmx800m`（降低堆内存）
- 或提高 `deploy.limits.memory: 1536M`（提高容器限制）
- **原则**：`-Xmx` 应该比 `limits.memory` 小 200M~400M

---

## 八、总结

### 部署流程速查

1. **服务器端**：
   - 添加 Docker Compose 配置（注意版本：BanyanDB 0.5.0）
   - 启动服务：`docker-compose up -d`
   - 配置防火墙：放行 `11800` 和 `52584`

2. **本地开发**：
   - 下载 Agent 并解压
   - IDEA 配置 VM Options
   - 启动服务并产生流量

3. **Gateway 特殊处理**：
   - 复制 `gateway-4.x` 和 `webflux-6.x` 插件
   - 删除旧版本插件
   - 重启服务

### 关键要点

- ✅ **版本匹配**：BanyanDB 0.5.0 + OAP 9.7.0
- ✅ **内存安全**：`-Xmx` < `Docker limit`（留 200M+ 余量）
- ✅ **时区统一**：所有服务添加 `TZ=Asia/Shanghai`
- ✅ **Gateway 插件**：Spring Boot 3.x 必须用 4.x/6.x 插件

---

**文档版本**: v1.0  
**最后更新**: 2025-12-24  
**适用环境**: Spring Boot 3.x + Spring Cloud Gateway 4.x

