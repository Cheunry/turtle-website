# 部署文档（初稿）

本文档描述后端（含网关）和前端的标准部署流程，基于 `deploy.sh` + `docker-compose.prod.yml`。

## 1. 部署架构

- **部署方式**：本地执行脚本，通过 SSH 上传镜像包到服务器并远程拉起容器。
- **编排方式**：Docker Compose。
- **服务清单**：
  - `novel-gateway`
  - `novel-user-service`
  - `novel-book-service`
  - `novel-search-service`
  - `novel-ai-service`
  - `turtle-website-front`

## 2. 部署前准备

1. 服务器可 SSH 连接。  
2. 服务器安装 Docker（支持 `docker compose` 或 `docker-compose`）。  
3. 服务器已创建外部网络 `common-network`。  
4. 服务器具备 Nacos、SkyWalking 等外部依赖可达性。  

## 3. 关键配置项

修改 `deploy.sh` 顶部配置：

- `SERVER_HOST`：目标服务器地址
- `SERVER_USER`：SSH 用户
- `SERVER_PATH`：服务器部署目录
- `SSH_KEY`：私钥路径（可空）
- `BACKEND_TAR`：后端镜像包路径
- `FRONTEND_TAR`：前端镜像包路径

## 4. 执行部署

```bash
cd turtle-website

# 默认：前后端一起部署
./deploy.sh

# 只部署后端
./deploy.sh --backend

# 只部署前端
./deploy.sh --frontend
```

部署脚本自动执行：
1. 连接服务器并准备目录  
2. 上传镜像包与 compose 文件  
3. 停止并移除旧容器  
4. 删除旧镜像并加载新镜像  
5. 按目标服务启动容器  

## 5. 部署后检查

在服务器执行：

```bash
docker compose -f /home/ubuntu/project/docker-compose.prod.yml ps
docker compose -f /home/ubuntu/project/docker-compose.prod.yml logs -f
```

重点关注：

- 网关是否正常监听并返回 2xx/4xx（非 5xx）
- 各服务是否注册到 Nacos
- RocketMQ 消费是否正常
- 搜索服务与 ES 连接是否成功

## 6. 常用运维命令

```bash
# 查看所有日志
docker compose -f /home/ubuntu/project/docker-compose.prod.yml logs -f

# 查看某个服务日志
docker compose -f /home/ubuntu/project/docker-compose.prod.yml logs -f novel-book-service

# 查看服务状态
docker compose -f /home/ubuntu/project/docker-compose.prod.yml ps

# 重启某个服务
docker compose -f /home/ubuntu/project/docker-compose.prod.yml restart novel-gateway

# 停止全部
docker compose -f /home/ubuntu/project/docker-compose.prod.yml down
```

## 7. 回滚策略（建议）

当前项目可采用“镜像回滚”：

1. 保留上一版镜像 tar 包  
2. 重新执行 `docker load` 导入旧镜像  
3. 重启对应服务  

后续可完善：
- 镜像版本号规范（避免总是 `latest`）
- 发布记录与回滚记录联动 `CHANGELOG.md`
