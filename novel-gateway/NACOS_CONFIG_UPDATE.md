# Nacos 配置更新说明

## 需要更新的配置

Nacos 上的 `novel-gateway.yml` 配置文件需要添加以下优化配置，以提升 Gateway 性能和稳定性。

## 更新后的完整配置

```yaml
server:
  port: 8888
spring:
  profiles:
    active: dev
  cloud:
    # 负载均衡器配置（优化服务发现失败时的处理）
    loadbalancer:
      # 启用重试机制
      retry:
        enabled: true
    gateway:
      # 全局超时配置
      httpclient:
        connect-timeout: 5000      # 连接超时（毫秒）
        response-timeout: 10s       # 响应超时
      # 全局过滤器配置
      default-filters:
        - DedupeResponseHeader=Access-Control-Allow-Credentials Access-Control-Allow-Origin
      routes:
        - id: novel-resource-front
          uri: lb://novel-resource-service
          predicates:
            - Path=/api/front/resource/**
        - id: novel-user-front
          uri: lb://novel-user-service
          predicates:
            - Path=/api/front/user/**
        - id: novel-book-front
          uri: lb://novel-book-service
          predicates:
            - Path=/api/front/book/**
        - id: novel-search-front
          uri: lb://novel-search-service
          predicates:
            - Path=/api/front/search/**
        - id: novel-author
          uri: lb://novel-user-service
          predicates:
            - Path=/api/author/**
        - id: novel-ai-front
          uri: lb://novel-ai-service
          predicates:
            - Path=/api/front/ai/**

# Actuator 端点管理
management:
  # 端点公开配置
  endpoints:
    # 通过 HTTP 公开的 Web 端点
    web:
      exposure:
        # 公开所有的 Web 端点
        include: "*"
  # 端点启用配置
  endpoint:
    logfile:
      # 启用返回日志文件内容的端点
      enabled: true
      # 外部日志文件路径
      external-file: logs/novel-gateway.log
  info:
    env:
      # 公开所有以 info. 开头的环境属性
      enabled: true
```

## 新增配置说明

### 1. 负载均衡器配置 (`loadbalancer`)
```yaml
loadbalancer:
  retry:
    enabled: true
```
**作用**：
- 启用重试机制，当服务调用失败时自动重试
- 有助于在 Nacos 服务发现不稳定时提供更好的容错能力

**注意**：移除了健康检查配置，因为 Spring Cloud LoadBalancer 的健康检查配置格式较复杂，且当前版本可能不完全支持。

### 2. Gateway HTTP 客户端超时配置 (`httpclient`)
```yaml
gateway:
  httpclient:
    connect-timeout: 5000      # 连接超时（毫秒）
    response-timeout: 10s       # 响应超时
```
**作用**：
- 设置连接超时为 5 秒，避免长时间等待
- 设置响应超时为 10 秒，防止请求无限期挂起
- 提升 Gateway 的响应性能和稳定性

### 3. 全局过滤器配置 (`default-filters`)
```yaml
gateway:
  default-filters:
    - DedupeResponseHeader=Access-Control-Allow-Credentials Access-Control-Allow-Origin
```
**作用**：
- 去重响应头，避免 CORS 相关的重复响应头
- 解决跨域请求时可能出现的响应头重复问题

## 更新步骤

1. 登录 Nacos 控制台：`http://120.53.89.103:52582/nacos`
2. 进入 **配置管理** -> **配置列表**
3. 找到 `novel-gateway.yml` 配置文件
4. 点击 **编辑** 按钮
5. 将上述完整配置复制粘贴到编辑框中
6. 点击 **发布** 保存配置
7. 重启 Gateway 服务使配置生效

## 注意事项

- 更新配置后，Gateway 服务会自动从 Nacos 拉取新配置（如果配置了自动刷新）
- 建议在更新前备份原配置
- 如果 Gateway 没有自动刷新配置，需要重启服务
- 这些配置优化了 Gateway 的性能和稳定性，特别是在 Nacos 连接不稳定时

## 验证

更新配置后，可以通过以下方式验证：

1. 查看 Gateway 启动日志，确认配置已加载
2. 测试 API 请求，确认路由正常工作
3. 检查服务发现是否正常（查看 Nacos 服务列表）
4. 观察 Gateway 日志，确认没有连接超时错误

