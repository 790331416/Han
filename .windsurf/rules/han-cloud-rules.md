# Han Cloud 项目规则与开发规范

## 强制版本约束

### 核心框架（禁止降级）
- Spring Boot: 4.0.2
- Spring Cloud: 2025.1.0
- Spring Cloud Alibaba: 2025.1.0.0
- Spring Framework: 7.0.3（由 Spring Boot 管理，勿手动指定）
- Java: 21（必须，Virtual Threads 依赖）

### 数据访问层
- mybatis-plus-spring-boot4-starter: 3.5.16（**必须用 boot4 后缀**，否则 Spring Boot 4.0 不兼容）
- mybatis-spring-boot-starter: 4.0.1（**必须显式覆盖** mybatis-plus 传入的 3.0.5 旧版本）
- pagehelper-spring-boot-starter: 2.1.0（2.2.0 不存在，禁止使用）
- dynamic-datasource-spring-boot4-starter: 4.5.0（**必须用 boot4 后缀**）

### 其他组件
- lombok: 1.18.38
- hutool-all: 5.8.36
- jjwt（io.jsonwebtoken）: 0.12.7
- jsoup: 1.19.1
- springdoc-openapi: 2.8.6
- knife4j-openapi3-jakarta-spring-boot-starter: 4.5.0
- flowable: 7.2.0
- redisson: 4.2.0
- transmittable-thread-local: 2.14.5
- micrometer-tracing-bridge-otel: 由 Spring Boot BOM 管理

---

## JDK 21 新特性（强制使用）

### 1. Record（不可变值对象）
**规则**：只读 VO / 响应子结构 / 事件对象 **必须** 使用 Java Record 替代 @Data class。
```java
// 正确 
@Builder
public record UserInfoVO(Long userId, String username, String nickname) {}

// 错误 
@Data @Builder
public static class UserInfoVO { private Long userId; ... }
```
**不适用**：需要 setter 的 DTO（请求绑定）、Entity（MyBatis/JPA 需 setter）、需要序列化的 LoginUser。

### 2. Pattern Matching for instanceof
**规则**：所有 instanceof 检查 **必须** 使用模式匹配，禁止显式类型转换。
```java
// 正确 
if (value instanceof String s) { use(s); }

// 错误 
if (value instanceof String) { use((String) value); }
```

### 3. Switch Expression
**规则**：优先使用 switch 表达式替代 switch 语句。非 sealed enum 的 switch **必须包含 default 分支**。
```java
return switch (clientType) {
    case PC -> PC_TOKEN_EXPIRE;
    case APP, H5 -> APP_TOKEN_EXPIRE;
    default -> PC_TOKEN_EXPIRE;  // 非 sealed enum 必须有 default
};
```

### 4. List.of / Map.of / Set.of
**规则**：不可变集合 **必须** 使用工厂方法，禁止 `Collections.unmodifiableList(new ArrayList<>())`。

---

## Spring Boot 4.0 / Spring Framework 7 新特性（强制使用）

### 1. Virtual Threads（Servlet 服务必须配置）
```yaml
spring:
  threads:
    virtual:
      enabled: true
```
**适用**：han-auth、han-system 及所有 Servlet 型业务服务。
**不适用**：han-gateway（WebFlux/Netty，Virtual Threads 无效）。

### 2. ProblemDetail RFC 7807（必须启用）
```yaml
spring:
  mvc:
    problemdetails:
      enabled: true
```
所有 Servlet 服务必须启用，让标准异常自动返回 RFC 7807 格式。

### 3. Duration API（时间配置强制使用）
**规则**：所有时间常量 **必须** 使用 `java.time.Duration`，禁止魔法数字。
```java
// 正确 
private static final Duration TOKEN_EXPIRE = Duration.ofMinutes(30);
redisTemplate.opsForValue().set(key, value, TOKEN_EXPIRE);

// 错误 
private static final long TOKEN_EXPIRE = 30 * 60;
redisTemplate.opsForValue().set(key, value, TOKEN_EXPIRE, TimeUnit.SECONDS);
```

### 4. @HttpExchange 声明式客户端（替代 Feign）
Spring 6 / Spring Boot 4.0 原生 HTTP 客户端，**禁止引入 OpenFeign**。

#### 定义接口（在 han-api-xxx 模块）
```java
@HttpExchange("/system")
public interface SystemServiceClient {
    @GetExchange("/user/{userId}")
    R<UserVO> getUserById(@PathVariable Long userId);
}
```

#### 启用自动注入（在启动类）
```java
@EnableHttpClients(basePackages = "com.han.api")
@SpringBootApplication
public class HanAuthApplication { }
```

### 5. Micrometer Observation API（全链路追踪）
已通过 `han-common-web` 自动引入 `micrometer-tracing-bridge-otel`。
所有依赖 `han-common-web` 的服务自动获得 HTTP 客户端、数据库调用的 Traces 和 Metrics。

---

## Lombok 使用规则

### Builder 模式
**规则**：属性超过 5 个的对象构建 **必须** 使用 @Builder，禁止超过 5 行连续 setter。
```java
// 正确 
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class LoginUser { ... }

LoginUser.builder().userId(1L).username("admin").build();

// 错误 
LoginUser user = new LoginUser();
user.setUserId(1L);
user.setUsername("admin");
// ... 15 行 setter
```

---

## 模块依赖规则

### han-system（单数据源服务）
- 直接依赖 `han-common-mybatis` + `postgresql` 驱动
- **禁止**依赖 `han-common-datasource`（会引入 dynamic-datasource）

### han-auth
- 必须排除 `DataSourceAutoConfiguration`
- 使用 `@EnableHttpClients` 代替手动 Bean 注册

### han-gateway
- WebFlux 响应式服务（spring-cloud-starter-gateway-server-webflux）
- **禁止**引入 spring-boot-starter-web 或 servlet 依赖
- **禁止**启用 Virtual Threads
- **注意**：Gateway MVC starter 尚未纳入 Spring Cloud 2025.1.0 BOM

### han-common-web（所有 Servlet 服务的公共依赖）
自动传递以下依赖，子模块无需重复引入：
- spring-boot-starter-actuator
- spring-cloud-starter-loadbalancer
- micrometer-tracing-bridge-otel

---

## ID 生成规则

### 雪花 ID
**规则**：默认使用 `XuIdUtil.snowflakeId()`（内部单例，线程安全）。
**禁止**在循环或高并发场景中调用 `new SnowflakeIdWorker()` 创建新实例。

---

## Maven 构建规则

### mybatis-plus 依赖写法
```xml
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-spring-boot4-starter</artifactId>
    <exclusions>
        <exclusion>
            <groupId>org.mybatis.spring.boot</groupId>
            <artifactId>mybatis-spring-boot-starter</artifactId>
        </exclusion>
    </exclusions>
</dependency>
<dependency>
    <groupId>org.mybatis.spring.boot</groupId>
    <artifactId>mybatis-spring-boot-starter</artifactId>
</dependency>
```

### IDE 编译冲突
IDE 会覆盖 `target/classes`，导致 MapStruct `*ConvertImpl` 包含错误桩代码。
**解决方案**：Docker 构建前将 JAR 拷到临时目录。

---

## Docker 部署规则

### 网络
所有容器在 `han-network` bridge 网络内，容器间通过容器名通信。

---

## 禁止事项

1. **禁止** `mybatis-plus-spring-boot3-starter`（已过时）
2. **禁止** `druid-spring-boot-starter`（不兼容 Spring Boot 4.0）
3. **禁止** 在 han-gateway 引入 servlet 依赖
4. **禁止** 手动注册 @HttpExchange 客户端 Bean
5. **禁止** 降级任何已升级的依赖版本
6. **禁止** pagehelper 2.2.0（不存在）
7. **禁止** instanceof 后显式类型转换（必须用 pattern matching）
8. **禁止** 使用 TimeUnit + 魔法数字设置 Redis 过期时间（必须用 Duration）
9. **禁止** 超过 5 行连续 setter 构建对象（必须用 Builder）
10. **禁止** 只读值对象使用 @Data class（必须用 Record）
