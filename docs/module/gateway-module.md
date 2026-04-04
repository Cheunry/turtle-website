# 网关模块详解（面试版）

本文档用于单独讲清 `novel-gateway`，可直接用于面试表述和项目复盘。

## 1. 模块定位

- **一句话**：网关是系统唯一对外入口，负责统一接入与流量治理。
- **核心职责**：
  - 路由转发（按路径转发到对应微服务）
  - 跨域处理（统一 CORS）
  - 请求/响应日志记录（全局过滤器）
  - 服务发现与负载均衡（`lb://` + Nacos）

## 2. 为什么选择 Spring Cloud Gateway

### 2.1 选型结论

- 采用 Spring Cloud Gateway，不采用 Zuul。

### 2.2 主要理由

1. **性能模型更优**：基于 WebFlux + Netty 的非阻塞模型，适合高并发。  
2. **生态兼容更好**：与 Spring Boot 3.x、Spring Cloud 2024.x、Nacos 集成顺畅。  
3. **扩展机制成熟**：`GlobalFilter` / `GatewayFilter` 可快速扩展日志、鉴权、限流能力。  
4. **维护成本更低**：Spring 官方主线维护，资料完整，长期演进稳定。  

## 3. 当前实现拆解（基于代码）

### 3.1 基础依赖

`novel-gateway/pom.xml` 关键依赖：

- `spring-cloud-starter-gateway`
- `spring-cloud-starter-loadbalancer`
- `spring-cloud-starter-alibaba-nacos-discovery`
- `spring-cloud-starter-alibaba-nacos-config`

### 3.2 全局日志过滤器 `LoggingFilter`

作用：

- 请求进入网关时记录 `Path/Method/Headers/QueryParams`
- 响应返回前记录状态码
- 通过 `Ordered.HIGHEST_PRECEDENCE` 保证尽早执行

价值：

- 快速确认路由是否命中
- 快速区分“网关层异常”还是“下游服务异常透传”

风险提示：

- Header 全量打印存在敏感信息泄露风险（如 token/cookie）
- 高峰期日志量大，会带来 IO 压力

### 3.3 跨域配置 `NovelCorsConfig`

当前行为：

- `addAllowedOriginPattern("*")`
- `addAllowedHeader("*")`
- `addAllowedMethod("*")`
- `setAllowCredentials(true)`

说明：

- 开发联调方便，但生产建议改为域名白名单，避免过度放开。

## 4. 请求流转（可背诵版）

1. 客户端请求进入 `novel-gateway`。  
2. 全局过滤器记录请求信息。  
3. 网关根据路由规则匹配目标服务。  
4. 通过 Nacos 服务发现 + LoadBalancer 选择实例。  
5. 请求转发至下游服务并获取响应。  
6. 网关记录响应状态，返回给客户端。  

## 5. 线上排障 SOP（网关维度）

## 5.1 典型现象：请求 5xx

按顺序排查：

1. 网关容器是否正常运行，端口是否可达。  
2. 网关日志中是否看到该请求路径。  
3. Nacos 中目标服务是否已注册且健康。  
4. 路由配置是否正确（环境/namespace/group/dataId）。  
5. 下游服务是否本身报错（网关只是透传）。  

## 5.2 典型现象：浏览器跨域失败

排查点：

- 请求是否经过网关
- 预检请求 OPTIONS 是否成功
- 凭证请求与 CORS 配置是否匹配

## 5.3 典型现象：504 超时

排查点：

- 是网关超时还是下游慢查询
- 网关超时配置是否小于业务耗时
- 下游依赖是否成为瓶颈（DB/ES/第三方 API）

## 6. 面试可讲的优化计划

### 6.1 安全与合规

- 对日志做脱敏（Authorization/Cookie/手机号等）
- CORS 改为白名单域名
- 统一前置 JWT 校验与免鉴权路径白名单

### 6.2 稳定性

- 增加网关层限流（IP + 用户维度）
- 增加熔断/降级策略，避免下游故障扩散

### 6.3 可观测性

- 补齐网关指标看板：QPS、错误率、P95/P99、路由命中率
- 引入 traceId 贯穿网关到下游，提升跨服务排障效率

## 7. 现状与改进总结

- **现状优势**：路由与入口治理清晰，基础可用性与可排障能力已具备。  
- **当前短板**：安全策略、限流熔断、指标体系仍有完善空间。  
- **演进方向**：从“可用网关”升级到“具备生产级治理能力的网关”。  
