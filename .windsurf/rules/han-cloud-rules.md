---
trigger: always_on
---
# Han Cloud 项目规则与开发规范

## 强制版本约束

### 核心框架（禁止降级）
- Spring Boot: 4.1.0
- Spring Cloud: 2025.1.2
- Spring Cloud Alibaba: 2025.1.0.0
- Spring Framework: 7.0.8（由 Spring Boot 4.1 管理，勿手动指定）
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

### 前端技术栈（han-ui）
- Vue: 3.5.x
- Vite: 6.x
- TypeScript: 5.7.x
- Element Plus: 2.9.x
- UnoCSS: 66.x（原子化 CSS，替代手写 SCSS）
- VueUse: 14.x（组合式工具库）
- Pinia: 2.3.x（状态管理）
- Axios: 1.7.x（HTTP 客户端）
- **pnpm**: 10.x（包管理器，**禁止 npm/yarn**）
- UI 主题：现代极简白（Notion/Linear 风格，蓝色主色 `#2563eb`）

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

## 工具类使用规范

### Han*Util 统一工具类

所有通用工具类统一使用 `Han` 前缀，位于 `com.han.common.core.util` 包：

| 工具类 | 用途 |
|--------|------|
| `HanJsonUtil` | JSON 序列化/反序列化（基于 Jackson） |
| `HanStrUtil` | 字符串操作 |
| `HanIdUtil` | ID 生成（UUID、雪花ID） |
| `HanCollUtil` | 集合操作 |
| `HanSecureUtil` | 加密/哈希/Base64 |

**规则**：
- 所有 JSON 操作**必须**通过 `HanJsonUtil` 执行，禁止直接注入或使用 `ObjectMapper`
- 业务模块中**禁止**直接引用 Hutool（`cn.hutool.*`），必须通过 `han-common-core` 中的 `Han*Util` 封装类使用
- 如需 Hutool 新功能，先在 `Han*Util` 中封装对应方法，再在业务代码中调用

### import 引用风格

**规则**：所有类引用**必须**通过 `import` 语句导入，然后使用简短类名调用。

```java
// 正确 
import com.han.common.core.util.HanJsonUtil;

String json = HanJsonUtil.toJsonString(obj);

// 错误 — 禁止全包名调用
String json = com.han.common.core.util.HanJsonUtil.toJsonString(obj);
```

**唯一例外**：当同一文件存在类名冲突时，可使用全包名调用。

---

## ID 生成规则

### 雪花 ID
**规则**：默认使用 `HanIdUtil.snowflakeId()`（内部单例，线程安全）。
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
IDE 会覆盖 `target/classes`，导致 MapStruct `*ConverterImpl` 包含错误桩代码。
**解决方案**：Docker 构建前将 JAR 拷到临时目录。

---

## 数据删除规范

### 逻辑删除（默认策略）

所有继承 `BaseEntity` 的实体自动拥有 `@TableLogic delFlag` 字段，MyBatis-Plus 的 `removeById` / `removeBatchByIds` 会执行 `UPDATE SET del_flag = 1`。

**适用场景**（使用逻辑删除）：
- 所有**业务主表**：`sys_user`、`sys_role`、`sys_dept`、`sys_menu`、`sys_post`、`sys_notice`、`sys_config`、`sys_tenant`、`sys_tenant_package`、`sys_dict_type`、`sys_dict_data`、`sys_job` 等
- 所有继承 `BaseEntity` / `BizEntity` / `TenantEntity` / `TreeEntity` 的 PO 类
- 任何含有 `del_flag` 列的表

**规则**：
- 自定义 XML 查询**必须**手动添加 `WHERE del_flag = 0` 条件（MyBatis-Plus 仅对自动生成的 SQL 自动追加）
- 联表查询中，**每个表**都要加 `AND x.del_flag = 0`
- 逻辑删除后数据仍占存储空间，定期归档或清理由 DBA 运维

### 物理删除（仅限关联表）

**适用场景**（使用物理删除 `DELETE FROM`）：
- **纯关联表**（无 `del_flag` 列）：`sys_user_role`、`sys_user_post`、`sys_role_menu`、`sys_role_dept`、`sys_tenant_package_menu` 等
- **日志类表**（按策略定期清理）：`sys_oper_log`、`sys_login_log`

**规则**：
- 关联表的增删直接使用 `INSERT` / `DELETE`，不使用 MyBatis-Plus 的 `removeById`
- 日志表清理由运维任务执行，业务代码**禁止**物理删除日志

---

## Long ID 前后端序列化规范

### 问题背景
雪花 ID 为 19 位 `Long`，超过 JavaScript `Number.MAX_SAFE_INTEGER`（2^53 - 1 = 9007199254740991，16 位），前端会丢失精度导致 ID 错误。

### 后端规则
- `han-common-web` 已通过 `JacksonAutoConfiguration` 全局配置：`Long`（包装类型）序列化为 **JSON String**
- `long`（原始类型）保持为 **JSON Number**（用于 `total`、`count` 等计数字段）
- **PO / VO / DTO 中所有 ID 字段必须使用 `Long`（包装类型）**，禁止使用 `long`（原始类型）
- **计数字段使用 `long`（原始类型）**，如 `PageResult.total`

### 前端规则
- TypeScript 中所有 ID 类型声明为 `string | number`
- API 函数参数中 ID 使用 `string | number`
- URL 路径拼接中 ID 直接使用模板字符串 `` `/path/${id}` ``，无需额外转换
- **禁止**对 ID 做数学运算（如 `id + 1`）

---

## Docker 部署规则

### 网络
所有容器在 `han-network` bridge 网络内，容器间通过容器名通信。

---

## 用户名引用策略

### 活跃引用（当前负责人、当前处理人等）
- **只存用户 ID**（如 `leader_id BIGINT`），查询时 **LEFT JOIN `sys_user`** 取 `nickname`
- 用户改名后数据**自动同步**，零维护代码
- 适用场景：`sys_dept.leader_id`、未来的审批人、任务负责人等

### 历史快照（操作日志、审计记录等）
- **存文本**（如 `oper_name VARCHAR`），**不做同步**
- 保留操作发生时刻的用户名，确保审计可追溯
- 适用场景：`sys_oper_log.oper_name`、`sys_login_log.username`

### 大数据量场景补充
- 部门等**天然小表**（几百~几千条）：直接 JOIN，无性能问题
- 工单/审批等**大数据量表**（十万+）：可采用**冗余字段 + 异步消息同步**，用户改名时通过事件批量更新关联记录
- 选择依据：**查询频率 vs 数据量**，小表优先 JOIN，大表酌情冗余

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
11. **禁止** 业务模块直接引用 `spring-boot-starter-data-redis`（必须通过 `han-common-redis` 封装组件引入）
12. **禁止** 业务模块直接引用 `spring-boot-starter-data-redis` 或 `redisson-spring-boot-starter`，Redis 相关功能统一通过 `han-common-redis`（基础 Redis 操作）或 `han-starter-cache`（缓存抽象）引入
13. **禁止** 在 `han-common-*` 封装层之外直接使用 Spring Boot 原生 starter（如需 Redis/MQ/OSS 等能力，必须通过对应的 `han-common-*` 或 `han-starter-*` 组件引入）
14. **禁止** 对业务主表执行物理删除（`DELETE FROM`），必须使用逻辑删除（`removeById` → `UPDATE del_flag = 1`）
15. **禁止** 自定义 XML 查询中遗漏 `del_flag = 0` 条件（MyBatis-Plus 仅在自动生成 SQL 时追加，手写 XML 必须显式添加）
16. **禁止** PO/VO/DTO 中 ID 字段使用 `long` 原始类型（必须用 `Long` 包装类型，确保 Jackson 序列化为字符串）
17. **禁止** 前端对后端返回的 ID 做数学运算或假设为 `number` 类型（ID 一律视为 `string | number`）
22. **禁止** 新增含 Long ID 字段的类时使用 `long` 原始类型（必须用 `Long` 包装类型）

---

## Long ID 序列化规范

### 背景
JavaScript `Number` 最大安全整数为 `2^53 - 1`，雪花算法生成的 Long ID 超过此范围会导致前端精度丢失。

### 方案（Jackson 3 全局序列化）

Spring Boot 4.0 默认使用 **Jackson 3**（`tools.jackson.*`），Jackson 2（`com.fasterxml.jackson.databind.*`）的注解和 Module 对 HTTP 序列化**无效**。

通过 `JsonMapperBuilderCustomizer` 全局注册 `Long.class → ToStringSerializer`，配置位于 `han-common-web` 的 `JacksonAutoConfiguration`：

```java
// han-common-web: com.han.common.web.config.JacksonAutoConfiguration
@Bean
public JsonMapperBuilderCustomizer longToStringCustomizer() {
    return builder -> {
        SimpleModule module = new SimpleModule("LongToString");
        // 仅 Long（包装类型，ID 字段）→ String
        // long（原始类型，total/timestamp/count）保持 Number
        module.addSerializer(Long.class, ToStringSerializer.instance);
        builder.addModule(module);
    };
}
```

### 类型使用规则

| 用途 | Java 类型 | JSON 输出 | 示例 |
|------|-----------|-----------|------|
| **ID 字段** | `Long`（包装类型） | `"2028764447060365313"` | `userId`, `deptId`, `roleId` |
| **计数/时间戳** | `long`（原始类型） | `3` / `1772535638741` | `total`, `timestamp`, `costTime` |
| **普通整数** | `int` / `Integer` | `200` / `10` | `code`, `pageSize`, `status` |

### Jackson 版本说明

- Spring Boot 4.0 HTTP 序列化使用 **Jackson 3**（`tools.jackson.core:jackson-databind:3.0.4`）
- `jackson-annotations` 包名**未变**（仍为 `com.fasterxml.jackson.annotation`），`@JsonIgnore` / `@JsonProperty` 等注解仍有效
- `jackson-databind` 已迁移至 `tools.jackson.*`，因此 Jackson 2 的 `@JsonSerialize(using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)` 对运行时序列化**无效**
- 现有代码中的 Jackson 2 `@JsonSerialize` 注解保留作为**文档标识**，实际序列化由全局 `SimpleModule` 控制

### 前端配合

前端 TypeScript 中所有 ID 字段类型声明为 `string | number`：
```typescript
interface User {
  userId: string | number
  deptId: string | number
  // ...
}
```

---

## 其他禁止事项

18. **禁止** 直接注入或使用 `ObjectMapper`（JSON 操作必须通过 `HanJsonUtil`）
19. **禁止** 以全包名方式调用类方法（必须先 `import` 再用简短类名调用，类名冲突除外）
20. **禁止** 业务模块直接引用 Hutool（`cn.hutool.*`），必须通过 `han-common-core` 中的 `Han*Util` 封装类使用
21. **禁止** 使用旧前缀 `Xu*Util` 类名（已统一重命名为 `Han*Util`）
