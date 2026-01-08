# Nacos 选型与 gRPC 警告 - 面试回答

## 问题：为什么选 Nacos 而不是 Eureka/Consul？遇到过 Nacos 的 gRPC 警告吗？

---

## 回答

我们项目选择了 **Nacos**，主要考虑几个方面。

### 为什么选 Nacos

**第一个是功能集成度高**。Nacos 既是服务注册中心，也是配置中心，一个组件解决两个问题。我们项目里，服务注册、配置管理、动态刷新都用 Nacos，不需要额外引入 Spring Cloud Config。而且 Nacos 的配置管理功能很强大，支持多环境配置、配置分组、配置版本历史，还能通过 `extension-configs` 扩展配置，比如我们把 RocketMQ、Redis 的配置都放在 Nacos 上统一管理。

**第二个是灵活性和性能**。Nacos 支持 AP 和 CP 两种模式，可以根据场景切换。默认 AP 模式适合服务注册发现，保证高性能和可用性。如果要做分布式协调，可以切到 CP 模式。而且 Nacos 的健康检查机制比较完善，既支持客户端心跳，也支持服务端主动探测，能更及时地发现服务异常。

**第三个是生态和社区**。我们用的是 Spring Cloud Alibaba，Nacos 是阿里开源的，对 Spring Cloud 支持非常好，集成简单。而且国内社区活跃，文档和中文资料多，遇到问题容易找到解决方案。相比之下，Eureka 2.0 已经停止开发，1.x 版本只维护，不太适合新项目。

**第四个是实际使用体验**。我们项目里，网关、用户服务、书籍服务、搜索服务、AI 服务都注册到 Nacos，配置也统一管理。通过 Nacos 控制台可以直观地看到服务列表、健康状态、配置内容，运维很方便。而且 Nacos 支持命名空间和分组，可以很好地隔离不同环境的配置。

### 关于 Eureka 和 Consul

**Eureka** 我们了解过，但它只负责服务注册发现，配置管理需要额外引入 Spring Cloud Config，而且 Eureka 2.0 已经停止开发，1.x 版本只维护，不太适合新项目。

**Consul** 功能很强大，支持服务网格，但对我们来说有点重。我们主要是 Java 服务，不需要跨语言的服务网格功能。而且 Consul 强调一致性，基于 Raft 协议，在 Leader 选举期间服务发现不可用，对可用性有一定影响。

### 遇到过 gRPC 警告吗？

**遇到过**。开发初期遇到过 Nacos 的 gRPC 警告，主要是 `ClassNotFoundException: com.alibaba.nacos.shaded.io.grpc.census.InternalCensusStatsAccessor` 这类警告。

**原因分析**：
- Nacos 客户端使用 gRPC 进行通信，内部尝试加载 Census（统计和追踪）相关类
- 这些类在某些版本中可能不存在，导致 `ClassNotFoundException`
- 不过 Nacos 客户端已经处理了这种情况，会继续使用其他方式，**不影响实际功能**

**解决方案**：
我们通过调整日志级别来处理。在 `logback-spring.xml` 中过滤这些警告日志：

```xml
<logger name="com.alibaba.nacos.shaded.io.grpc" level="INFO" additivity="false">
    <appender-ref ref="STDOUT"/>
    <appender-ref ref="FILE"/>
</logger>
```

或者在 `application.yml` 中配置：

```yaml
logging:
  level:
    com.alibaba.nacos.shaded.io.grpc: INFO
```

配置后这些警告就消失了，服务注册、配置拉取、服务发现等功能都正常。

**其他可能触发 gRPC 警告的情况**：
- **端口未开放**：Nacos 除了默认的 8848 端口，还有两个 gRPC 端口（9848、9849），如果云服务器安全组没开放会触发警告
- **网络问题**：Nginx/负载均衡配置错误、连接超时等
- **版本兼容性**：Nacos 客户端和服务端版本不匹配

不过我们项目里主要是 Census 类的警告，通过日志级别过滤就解决了。

### 总结

整体来说，选择 Nacos 主要是因为它**功能集成度高、性能好、生态完善、运维方便**。实际使用下来，服务注册发现很稳定，配置管理也很方便，特别是支持动态刷新，修改配置后服务能自动感知，不需要重启。gRPC 警告虽然遇到过，但通过日志配置就能解决，不影响功能。

---

## 可能的追问点

### 1. Nacos 的 AP 和 CP 模式有什么区别？
AP 模式（默认）强调可用性和分区容错性，适合服务注册发现，即使部分节点挂了也能提供服务列表。CP 模式强调一致性，适合做分布式协调，类似 Zookeeper。我们项目用的是 AP 模式，因为服务注册发现对一致性要求不高，更看重可用性。

### 2. Nacos 配置中心如何实现动态刷新？
Nacos 配置中心支持配置变更推送，客户端会监听配置变化，一旦配置更新，会主动推送给客户端，客户端收到后触发 Spring 的 `@RefreshScope` 机制刷新配置。我们项目里，RocketMQ、Redis 等配置都放在 Nacos 上，修改后服务能自动感知，不需要重启。

### 3. Nacos 服务发现失败怎么办？
我们通过几个机制保证：一是 Nacos 的健康检查，能及时发现服务异常；二是 Spring Cloud LoadBalancer 的重试机制，服务调用失败会自动重试；三是 Feign 的 fallback 降级，服务不可用时返回默认值。而且我们会在 Nacos 控制台监控服务状态，发现问题及时处理。

### 4. Nacos 配置中心如何管理多环境配置？
我们通过 `spring.profiles.active` 指定环境，比如 dev、test、prod，然后在 Nacos 上创建对应的配置文件，比如 `novel-book-service-dev.yml`。这样不同环境可以有不同的配置，而且可以通过命名空间进一步隔离。

### 5. Nacos 的性能如何？
我们项目里，服务注册发现延迟在毫秒级，配置拉取也很快。Nacos 支持集群部署，可以水平扩展，性能不是瓶颈。而且 Nacos 的配置是持久化的，即使服务重启，配置也不会丢失。

### 6. 如果 Nacos 服务器挂了怎么办？
我们项目里，Nacos 是单机部署的，如果挂了确实会影响服务注册发现。生产环境建议部署 Nacos 集群，保证高可用。而且我们会在 Nacos 控制台监控服务状态，发现问题及时处理。如果 Nacos 临时不可用，服务会使用本地缓存的服务列表，虽然可能不是最新的，但能保证基本可用。
