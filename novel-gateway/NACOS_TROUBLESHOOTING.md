# Nacos 连接问题排查指南

## 问题描述

Gateway 服务报错：`Client not connected, current status:UNHEALTHY`

## 可能的原因

1. **Nacos 服务器未启动或不可访问**
   - 检查 Nacos 服务器 `120.53.89.103:52582` 是否正常运行
   - 检查网络连接是否正常

2. **防火墙或网络策略阻止连接**
   - 检查本地防火墙设置
   - 检查服务器防火墙规则

3. **Nacos 服务器地址配置错误**
   - 确认服务器地址和端口是否正确

4. **Nacos 认证问题**
   - 如果 Nacos 启用了认证，需要在配置中添加用户名和密码

## 解决方案

### 方案1：检查 Nacos 服务器状态

```bash
# 测试 Nacos 服务器是否可访问
curl http://120.53.89.103:52582/nacos/v1/ns/service/list

# 或者使用 telnet 测试端口
telnet 120.53.89.103 52582
```

### 方案2：使用本地 Nacos（开发环境）

如果 Nacos 服务器不可用，可以修改 `bootstrap.yml` 使用本地 Nacos：

```yaml
spring:
  cloud:
    nacos:
      server-addr: localhost:8848  # 改为本地 Nacos
```

然后启动本地 Nacos：
```bash
# 下载 Nacos
# 启动 Nacos（单机模式）
sh startup.sh -m standalone
```

### 方案3：添加 Nacos 认证配置

如果 Nacos 启用了认证，需要在 `bootstrap.yml` 中添加：

```yaml
spring:
  cloud:
    nacos:
      username: nacos
      password: nacos
```

### 方案4：检查服务注册状态

1. 访问 Nacos 控制台：`http://120.53.89.103:52582/nacos`
2. 检查服务列表，确认 `novel-book-service` 等服务是否已注册
3. 检查服务健康状态

### 方案5：临时禁用服务发现（仅用于测试）

如果暂时无法连接 Nacos，可以临时使用直连方式（不推荐生产环境）：

在 `application.yml` 中修改路由配置：

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: novel-book-front
          uri: http://localhost:8003  # 直接指定服务地址
          predicates:
            - Path=/api/front/book/**
```

## 日志分析

查看 Gateway 启动日志，关注以下信息：

1. **Nacos 连接日志**：查找 `NacosDiscoveryClient` 相关日志
2. **服务注册日志**：确认服务是否成功注册
3. **服务发现日志**：确认是否成功发现其他服务

## 常见错误

### 错误1：Client not connected, current status:UNHEALTHY
- **原因**：无法连接到 Nacos 服务器
- **解决**：检查网络连接和 Nacos 服务器状态

### 错误2：No servers available for service
- **原因**：服务未注册到 Nacos 或服务发现失败
- **解决**：检查服务是否正常启动并注册到 Nacos

### 错误3：Connection refused
- **原因**：Nacos 服务器未启动或端口错误
- **解决**：确认 Nacos 服务器地址和端口配置正确

## 验证步骤

1. 检查 Nacos 服务器是否可访问
2. 检查 Gateway 配置是否正确
3. 检查服务是否已注册到 Nacos
4. 检查网络连接和防火墙设置
5. 查看 Gateway 启动日志，确认连接状态

## 联系支持

如果以上方案都无法解决问题，请提供以下信息：
- Gateway 启动日志
- Nacos 服务器状态
- 网络连接测试结果
- 配置文件内容（隐藏敏感信息）

