# 面试回答：统一鉴权与JWT（详细版）

## 一、技术选型：JWT vs OAuth2

### 1.1 为什么选择JWT？

**我们选择了JWT作为统一鉴权方案，原因如下：**

1. **业务场景匹配**
   - JWT的无状态特性非常适合微服务架构，避免了服务间的session共享问题
   - 不需要像OAuth2那样复杂的授权服务器
   
2. **性能优势**
   - JWT是自包含的，服务端无需查询数据库或缓存即可验证token
   - 减少了网络开销，提升了性能
   - 特别适合高并发的微服务场景

3. **实现简单**
   - 相比OAuth2的授权码模式、客户端模式等复杂流程，JWT实现更轻量
   - 开发成本低，维护简单

4. **OAuth2的适用场景**
   - OAuth2更适合第三方授权场景（如微信登录、QQ登录）
   - 我们当前是自建用户体系，不需要复杂的授权流程
   - 如果未来需要接入第三方登录，可以在JWT基础上扩展OAuth2

### 1.2 为什么不选OAuth2？

- **复杂度高**：需要授权服务器、资源服务器、客户端等多个角色
- **性能开销**：每次请求可能需要访问授权服务器验证token
- **业务不匹配**：我们的场景是内部系统，不需要第三方授权

## 二、统一鉴权架构设计

### 2.1 分层鉴权策略

我们采用了**网关层 + 服务层**的分层鉴权策略：

#### 网关层（Spring Cloud Gateway）

```yaml
# novel-gateway/src/main/resources/application.yml
spring:
  cloud:
    gateway:
      routes:
        - id: novel-user-front
          uri: lb://novel-user-service
          predicates:
            - Path=/api/front/user/**
```

**当前状态**：网关负责路由转发，未做统一鉴权拦截（可扩展）

**可扩展方案**：可以在Gateway中添加全局过滤器，统一验证token，减少各服务重复代码

#### 服务层（拦截器模式）

我们实现了两种拦截器，支持不同的鉴权策略：

##### 1. 严格鉴权拦截器（AuthInterceptor）

**使用场景**：用户服务和作者中心，必须携带有效token

**代码实现**：

```java
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final CacheService cacheService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        // 获取登录 JWT
        String token = request.getHeader(SystemConfigConsts.HTTP_AUTH_HEADER_NAME);

        // 开始认证
        if (!StringUtils.hasText(token)) {
            // token 为空
            throw new BusinessException(ErrorCodeEnum.USER_LOGIN_EXPIRED);
        }
        Long userId = JwtUtils.parseToken(token, SystemConfigConsts.NOVEL_FRONT_KEY);
        if (Objects.isNull(userId)) {
            // token 解析失败
            throw new BusinessException(ErrorCodeEnum.USER_LOGIN_EXPIRED);
        }

        // 从缓存获取用户信息（优先使用缓存，减少数据库查询）
        UserInfo userInfo = cacheService.getUserInfo(userId);

        if (Objects.isNull(userInfo)) {
            // 用户不存在
            throw new BusinessException(ErrorCodeEnum.USER_ACCOUNT_NOT_EXIST);
        }

        // 设置 userId 到当前线程
        UserHolder.setUserId(userId);

        // 从缓存获取作者信息（优先使用缓存，减少数据库查询）
        AuthorInfo authorInfo = cacheService.getAuthorInfoByUserIdFromCache(userId);
        if (Objects.nonNull(authorInfo)) {
            UserHolder.setAuthorId(authorInfo.getId());
            // 存储作者笔名，避免后续重复查询
            UserHolder.setAuthorPenName(authorInfo.getPenName());
        }

        return HandlerInterceptor.super.preHandle(request, response, handler);
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, 
                                Object handler, Exception ex) throws Exception {
        // 清理当前线程保存的用户数据
        UserHolder.clear();
        HandlerInterceptor.super.afterCompletion(request, response, handler, ex);
    }
}
```

**配置拦截路径**：

```java
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 权限认证拦截
        registry.addInterceptor(authInterceptor)
                // 拦截会员中心相关请求接口
                .addPathPatterns(ApiRouterConsts.API_FRONT_USER_URL_PREFIX + "/**",
                        ApiRouterConsts.API_AUTHOR_URL_PREFIX + "/**")
                // 放行登录注册相关请求接口
                .excludePathPatterns(ApiRouterConsts.API_FRONT_USER_URL_PREFIX + "/register",
                        ApiRouterConsts.API_FRONT_USER_URL_PREFIX + "/login")
                .order(2);
    }
}
```

**关键点**：
- Token为空或解析失败，直接抛出异常
- 从Redis缓存获取用户信息，提升性能
- 使用ThreadLocal存储用户信息，避免参数传递
- 请求结束后清理ThreadLocal，防止内存泄漏

##### 2. 可选鉴权拦截器（TokenParseInterceptor）

**使用场景**：其他微服务（如书籍服务、搜索服务），有token则解析，无token也放行

**代码实现**：

```java
@Component
@RequiredArgsConstructor
public class TokenParseInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        // 获取登录 JWT
        String token = request.getHeader(SystemConfigConsts.HTTP_AUTH_HEADER_NAME);
        if (StringUtils.hasText(token)) {
            // 解析 token 并保存
            UserHolder.setUserId(JwtUtils.parseToken(token, SystemConfigConsts.NOVEL_FRONT_KEY));
        }
        return HandlerInterceptor.super.preHandle(request, response, handler);
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, 
                                Object handler, Exception ex) throws Exception {
        // 清理当前线程保存的用户数据
        UserHolder.clear();
        HandlerInterceptor.super.afterCompletion(request, response, handler, ex);
    }
}
```

**关键点**：
- Token存在则解析，不存在也不抛异常
- 适用于需要用户信息但非必须的场景（如个性化推荐）

### 2.2 JWT工具类实现

#### Token生成

```java
@UtilityClass
public class JwtUtils {

    private static final String SECRET = "E66559580A1ADF48CDD928516062F12E";
    private static final String HEADER_SYSTEM_KEY = "systemKeyHeader";

    /**
     * 根据用户ID生成JWT
     *
     * @param uid       用户ID
     * @param systemKey 系统标识
     * @return JWT
     */
    public static String generateToken(Long uid, String systemKey) {
        return Jwts.builder()
                .header()
                .add(HEADER_SYSTEM_KEY, systemKey)  // 系统标识，用于隔离不同系统
                .and()
                .subject(uid.toString())              // 用户ID作为subject
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
}
```

**关键设计**：
- **系统标识隔离**：Header中包含`systemKeyHeader`，区分前台（front）、作者（author）、后台（admin）等不同系统
- **用户ID作为Subject**：将用户ID存储在JWT的subject字段中
- **HMAC-SHA256签名**：使用对称加密算法，确保token未被篡改

#### Token解析

```java
/**
 * 解析JWT返回用户ID
 *
 * @param token     JWT
 * @param systemKey 系统标识
 * @return 用户ID
 */
public static Long parseToken(String token, String systemKey) {
    Jws<Claims> claimsJws;
    
    try {
        claimsJws = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(token);

        // 判断该 JWT 是否属于指定系统
        if (Objects.equals(claimsJws.getHeader().get(HEADER_SYSTEM_KEY), systemKey)) {
            return Long.parseLong(claimsJws.getPayload().getSubject());
        }
    } catch (JwtException e) {
        log.warn("JWT解析失败:{}", token);
    }
    return null;
}
```

**关键设计**：
- **签名验证**：验证token签名，确保未被篡改
- **系统标识验证**：验证系统标识是否匹配，防止token跨系统使用
- **异常处理**：解析失败返回null，由调用方处理

### 2.3 用户信息传递（ThreadLocal）

```java
@UtilityClass
public class UserHolder {

    private static final ThreadLocal<Long> userIdTL = new ThreadLocal<>();
    private static final ThreadLocal<Long> authorIdTL = new ThreadLocal<>();
    private static final ThreadLocal<String> authorPenNameTL = new ThreadLocal<>();

    public static void setUserId(Long userId) {
        userIdTL.set(userId);
    }

    public static Long getUserId() {
        return userIdTL.get();
    }

    public static void setAuthorId(Long authorId) {
        authorIdTL.set(authorId);
    }

    public static Long getAuthorId() {
        return authorIdTL.get();
    }

    public static void clear() {
        userIdTL.remove();
        authorIdTL.remove();
        authorPenNameTL.remove();
    }
}
```

**优势**：
- **避免参数传递**：不需要在每个方法中传递userId
- **线程安全**：ThreadLocal保证每个线程独立存储
- **自动清理**：在拦截器的`afterCompletion`中清理，防止内存泄漏

### 2.4 登录流程

```java
@Override
public RestResp<UserLoginRespDto> login(UserLoginReqDto dto) {
    String username = dto.getUsername();

    // 检查用户是否被锁定
    if (isUserLocked(username)) {
        throw new BusinessException(ErrorCodeEnum.USER_LOGIN_LIMIT);
    }

    // 查询用户信息
    UserInfo userInfo = findByUsername(dto.getUsername());
    if (Objects.isNull(userInfo)) {
        processLoginFailure(username);
        throw new BusinessException(ErrorCodeEnum.USER_ACCOUNT_NOT_EXIST);
    }

    // 验证密码
    String encryptedPassword = DigestUtils.md5DigestAsHex(
            dto.getPassword().getBytes(StandardCharsets.UTF_8));
    if (!Objects.equals(userInfo.getPassword(), encryptedPassword)) {
        processLoginFailure(username);
        throw new BusinessException(ErrorCodeEnum.USER_PASSWORD_ERROR);
    }

    // 登录成功，清除失败记录并生成token
    clearLoginFailureRecord(username);
    return buildLoginSuccessResponse(userInfo);
}

private RestResp<UserLoginRespDto> buildLoginSuccessResponse(UserInfo userInfo) {
    String token = JwtUtils.generateToken(userInfo.getId(), SystemConfigConsts.NOVEL_FRONT_KEY);
    UserLoginRespDto respDto = UserLoginRespDto.builder()
            .token(token)
            .uid(userInfo.getId())
            .nickName(userInfo.getNickName())
            .build();
    return RestResp.ok(respDto);
}
```

**安全机制**：
- **登录失败锁定**：连续5次失败锁定30分钟
- **密码加密**：使用MD5加密（可升级为BCrypt）
- **Token生成**：登录成功后生成JWT token

## 三、Token刷新机制

### 3.1 当前实现

**项目目前未实现token刷新机制**，每次登录生成新token，token无过期时间。

**优点**：
- 实现简单
- 用户体验好，不需要频繁刷新

**缺点**：
- 安全风险：token一旦泄露，永久有效
- 无法主动失效：用户修改密码后，旧token仍然有效

### 3.2 优化方案（可扩展）

#### 方案一：双Token方案（推荐）

**设计思路**：
- **Access Token**：短期有效（如2小时），用于业务请求，存储在内存
- **Refresh Token**：长期有效（如7天），存储在Redis，用于刷新Access Token

**实现代码**：

```java
// 1. 登录时生成双token
public RestResp<UserLoginRespDto> login(UserLoginReqDto dto) {
    // ... 验证用户身份 ...
    
    // 生成Access Token（2小时有效）
    String accessToken = JwtUtils.generateToken(userInfo.getId(), 
            SystemConfigConsts.NOVEL_FRONT_KEY, 2, TimeUnit.HOURS);
    
    // 生成Refresh Token（UUID）
    String refreshToken = UUID.randomUUID().toString();
    
    // Refresh Token存入Redis，7天过期
    String refreshKey = String.format("refresh:token:%d", userInfo.getId());
    redisTemplate.opsForValue().set(refreshKey, refreshToken, 7, TimeUnit.DAYS);
    
    return RestResp.ok(UserLoginRespDto.builder()
            .token(accessToken)
            .refreshToken(refreshToken)
            .uid(userInfo.getId())
            .nickName(userInfo.getNickName())
            .build());
}

// 2. 刷新接口
@PostMapping("/refresh")
public RestResp<UserLoginRespDto> refreshToken(@RequestBody RefreshTokenReqDto dto) {
    // 从Redis获取Refresh Token
    String refreshKey = String.format("refresh:token:%d", dto.getUserId());
    String storedRefreshToken = (String) redisTemplate.opsForValue().get(refreshKey);
    
    if (!Objects.equals(storedRefreshToken, dto.getRefreshToken())) {
        throw new BusinessException(ErrorCodeEnum.USER_LOGIN_EXPIRED);
    }
    
    // 生成新的Access Token
    String newAccessToken = JwtUtils.generateToken(dto.getUserId(), 
            SystemConfigConsts.NOVEL_FRONT_KEY, 2, TimeUnit.HOURS);
    
    // 可选：生成新的Refresh Token（轮换机制）
    String newRefreshToken = UUID.randomUUID().toString();
    redisTemplate.opsForValue().set(refreshKey, newRefreshToken, 7, TimeUnit.DAYS);
    
    return RestResp.ok(UserLoginRespDto.builder()
            .token(newAccessToken)
            .refreshToken(newRefreshToken)
            .build());
}
```

**JWT工具类扩展（支持过期时间）**：

```java
public static String generateToken(Long uid, String systemKey, long expiration, TimeUnit timeUnit) {
    return Jwts.builder()
            .header()
            .add(HEADER_SYSTEM_KEY, systemKey)
            .and()
            .subject(uid.toString())
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + timeUnit.toMillis(expiration)))
            .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
            .compact();
}
```

#### 方案二：网关层统一刷新

在Gateway全局过滤器中检测Access Token过期，自动使用Refresh Token刷新：

```java
@Component
public class TokenRefreshFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String token = request.getHeaders().getFirst("Authorization");
        
        if (StringUtils.hasText(token)) {
            try {
                // 解析token，检查是否过期
                Claims claims = JwtUtils.parseClaims(token);
                Date expiration = claims.getExpiration();
                
                // 如果token即将过期（如剩余时间小于30分钟），自动刷新
                if (expiration != null && 
                    expiration.getTime() - System.currentTimeMillis() < 30 * 60 * 1000) {
                    // 调用刷新接口，获取新token
                    String newToken = refreshAccessToken(token);
                    // 将新token添加到响应头
                    ServerHttpResponse response = exchange.getResponse();
                    response.getHeaders().add("New-Access-Token", newToken);
                }
            } catch (Exception e) {
                // token解析失败，继续执行
            }
        }
        
        return chain.filter(exchange);
    }
}
```

### 3.3 安全增强

#### 1. 单点登录（SSO）

同一用户仅保留一个有效Refresh Token：

```java
// 登录时，删除旧的Refresh Token
String refreshKey = String.format("refresh:token:%d", userInfo.getId());
redisTemplate.delete(refreshKey);  // 删除旧token
redisTemplate.opsForValue().set(refreshKey, refreshToken, 7, TimeUnit.DAYS);
```

#### 2. 黑名单机制

登出时将token加入Redis黑名单：

```java
// 登出接口
@PostMapping("/logout")
public RestResp<Void> logout(@RequestHeader("Authorization") String token) {
    Long userId = UserHolder.getUserId();
    
    // 将Access Token加入黑名单（剩余有效期内）
    String blacklistKey = String.format("token:blacklist:%s", token);
    redisTemplate.opsForValue().set(blacklistKey, "1", 2, TimeUnit.HOURS);
    
    // 删除Refresh Token
    String refreshKey = String.format("refresh:token:%d", userId);
    redisTemplate.delete(refreshKey);
    
    return RestResp.ok();
}

// 拦截器中检查黑名单
if (redisTemplate.hasKey("token:blacklist:" + token)) {
    throw new BusinessException(ErrorCodeEnum.USER_LOGIN_EXPIRED);
}
```

#### 3. Refresh Token轮换

Refresh Token使用后生成新的，提升安全性：

```java
// 刷新时生成新的Refresh Token
String newRefreshToken = UUID.randomUUID().toString();
redisTemplate.opsForValue().set(refreshKey, newRefreshToken, 7, TimeUnit.DAYS);
```

## 四、技术亮点总结

### 4.1 架构设计

1. **分层鉴权**：网关层 + 服务层，灵活可扩展
2. **双拦截器模式**：严格鉴权和可选鉴权，适应不同业务场景
3. **系统隔离**：通过系统标识区分不同业务系统

### 4.2 性能优化

1. **缓存优化**：用户信息优先从Redis获取，减少数据库查询
2. **ThreadLocal传递**：避免参数传递，提升代码简洁性
3. **无状态设计**：JWT无状态，服务端无需存储session

### 4.3 安全机制

1. **签名验证**：HMAC-SHA256确保token未被篡改
2. **系统标识隔离**：防止token跨系统使用
3. **登录失败锁定**：防止暴力破解
4. **ThreadLocal清理**：防止内存泄漏

## 五、未来优化方向

### 5.1 密钥管理

**当前问题**：密钥硬编码在代码中

**优化方案**：
```java
// 从Nacos配置中心读取
@Value("${jwt.secret}")
private String secret;
```

### 5.2 签名算法升级

**当前**：HMAC-SHA256（对称加密）

**优化**：RSA（非对称加密）
- 私钥用于签名，公钥用于验证
- 更安全，但性能略低

### 5.3 防重放攻击

**方案**：加入时间戳和随机数（nonce）

```java
public static String generateToken(Long uid, String systemKey) {
    return Jwts.builder()
            .header()
            .add(HEADER_SYSTEM_KEY, systemKey)
            .and()
            .subject(uid.toString())
            .issuedAt(new Date())
            .id(UUID.randomUUID().toString())  // 随机ID，防止重放
            .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
            .compact();
}
```

### 5.4 网关统一鉴权

**当前**：各服务独立实现鉴权

**优化**：在Gateway中统一验证token，减少重复代码

```java
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        
        // 白名单路径直接放行
        if (isWhitelist(path)) {
            return chain.filter(exchange);
        }
        
        // 验证token
        String token = request.getHeaders().getFirst("Authorization");
        if (!StringUtils.hasText(token)) {
            return unauthorized(exchange);
        }
        
        Long userId = JwtUtils.parseToken(token, SystemConfigConsts.NOVEL_FRONT_KEY);
        if (userId == null) {
            return unauthorized(exchange);
        }
        
        // 将userId添加到请求头，传递给下游服务
        ServerHttpRequest modifiedRequest = request.mutate()
                .header("X-User-Id", userId.toString())
                .build();
        
        return chain.filter(exchange.mutate().request(modifiedRequest).build());
    }
}
```

### 5.5 实现Token刷新机制

按照上述双Token方案实现，提升安全性和用户体验。

---

## 总结

我们的项目采用JWT实现统一鉴权，通过拦截器在服务层完成验证，利用ThreadLocal传递用户信息。当前未实现token刷新，但架构设计支持后续扩展。这种方案在保证安全性的同时，兼顾了性能和开发效率。

**核心优势**：
- 无状态设计，适合微服务架构
- 性能优秀，减少数据库查询
- 实现简单，维护成本低
- 可扩展性强，支持后续优化

**待优化点**：
- 实现Token刷新机制
- 密钥管理优化
- 网关统一鉴权
- 防重放攻击
