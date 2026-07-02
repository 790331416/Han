
# Han Cloud 项目规则文档

## 文档说明

本文档是 Han Cloud 企业级多租户微服务平台的**强制性规则文档**，所有开发、构建、部署、测试工作必须严格遵守。

---

## 目录

- [第一部分：开发规则](#第一部分开发规则)
  - [1. 技术栈强制约束](#1-技术栈强制约束)
  - [2. AIB 架构规范](#2-aib-架构规范)
  - [3. 代码编写规范](#3-代码编写规范)
  - [4. API 接口规范](#4-api-接口规范)
  - [5. 安全规范](#5-安全规范)
  - [6. 数据传输规范](#6-数据传输规范)
- [第二部分：构建部署规则](#第二部分构建部署规则)
  - [1. 环境准备规则](#1-环境准备规则)
  - [2. Maven 构建规则](#2-maven-构建规则)
  - [3. Docker 部署规则](#3-docker-部署规则)
  - [4. Kubernetes 部署规则](#4-kubernetes-部署规则)
  - [5. 运维管理规则](#5-运维管理规则)
- [第三部分：测试规则](#第三部分测试规则)
  - [1. 单元测试规范](#1-单元测试规范)
  - [2. 接口测试规范](#2-接口测试规范)
  - [3. 架构测试规范](#3-架构测试规范)
  - [4. 测试覆盖率要求](#4-测试覆盖率要求)

---

# 第一部分：开发规则

## 1. 技术栈强制约束

### 1.1 核心技术栈（强制锁定版本）

| 技术组件 | 版本 | 用途 | 强制要求 |
|---------|------|------|---------|
| **JDK** | 21 | Java 运行环境 | **强制**，不得使用其他版本 |
| **Maven** | 3.9+ | 构建工具 | 推荐版本，最低 3.8+ |
| **Spring Boot** | 4.1.0 | 微服务框架 | **锁定版本** |
| **Spring Cloud** | 2025.1.2 | 微服务治理 | **锁定版本** |
| **Spring Cloud Alibaba** | 2025.1.0.0 | 服务注册/配置 | **锁定版本** |
| **PostgreSQL** | 18.1 | 主数据库 | **锁定版本** |
| **Redis** | 7 | 缓存/分布式锁 | **锁定版本** |
| **Nacos** | 3.1+ | 注册/配置中心 | **最低版本** |

### 1.2 JSON 处理 - Jackson（唯一标准）

**强制规则**：
- ✅ **必须使用** `com.fasterxml.jackson` 系列库
- ❌ **禁止使用** Gson、Fastjson、Fastjson2

**原因**：Spring Boot 默认集成，避免序列化冲突，安全性更优

**代码示例**：
```java
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

public class UserDto {
    @JsonProperty("user_name")
    private String userName;
    
    @JsonIgnore
    private String password;
}
```

### 1.3 HTTP 客户端 - HttpExchange（唯一标准）

**强制规则**：
- ✅ **必须使用** Spring 6 的 `@HttpExchange` 声明式客户端
- ❌ **禁止使用** OpenFeign、RestTemplate、OkHttp、Apache HttpClient

**代码示例**：
```java
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.GetExchange;

@HttpExchange("/system")
public interface SystemServiceClient {
    @GetExchange("/user/{id}")
    R<UserDto> getUserById(@PathVariable Long id);
}
```

### 1.4 对象映射 - MapStruct（唯一标准）

**强制规则**：
- ✅ **必须使用** MapStruct 进行 PO/DTO 转换
- ❌ **禁止使用** BeanUtils.copyProperties、手动转换、Dozer

**代码示例**：
```java
@Mapper(componentModel = "spring")
public interface UserConverter {
    
    @Mapping(source = "userId", target = "id")
    UserDto toDto(UserPo po);
}
```

### 1.5 工具类 - Hutool（间接使用）

**强制规则**：
- ✅ **允许使用** Hutool 工具包，但**必须通过基础工具类封装后使用**
- ❌ **禁止直接引入** `cn.hutool` 包，禁止在业务代码中直接调用

**正确做法**：
```java
import com.han.common.core.util.StringUtils;

public void process(String text) {
    if (StringUtils.isEmpty(text)) {
        return;
    }
}
```

**禁止做法**：
```java
import cn.hutool.core.util.StrUtil;  // 禁止直接导入

public void process(String text) {
    if (StrUtil.isEmpty(text)) {  // 禁止直接调用
        return;
    }
}
```

### 1.6 禁止引入的依赖

以下依赖**严格禁止**添加到 `pom.xml`：

```xml
<!-- 禁止的依赖清单 -->
<dependency>
    <groupId>com.alibaba</groupId>
    <artifactId>fastjson</artifactId>  <!-- 禁止 -->
</dependency>

<dependency>
    <groupId>com.google.code.gson</groupId>
    <artifactId>gson</artifactId>  <!-- 禁止 -->
</dependency>

<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-openfeign</artifactId>  <!-- 禁止 -->
</dependency>
```

---

## 2. AIB 架构规范

### 2.1 三层架构定义

| 层级 | 全称 | 职责 | 路由前缀 | 注解要求 |
|------|------|------|---------|----------|
| **B** | **BaseController** | 持有 Service，实现通用 CRUD 逻辑 | 无 | **不允许** `@RestController` |
| **A** | **AdminController** | 管理端接口，处理 UI 请求 | `/admin` | **必须** `@RestController("Bean名")` + `@PreAuthorize` |
| **I** | **InnerController** | 微服务内部 RPC 调用 | `/inner` | **必须** `@RestController("Bean名")` + `@InnerAuth` |

### 2.2 B 层（Base Controller）规范

**职责**：承载模块的核心业务逻辑流转

**强制要求**：
- 继承通用的 `BaseController` 或 `TreeController`
- 通过泛型绑定具体的 `Query`、`Dto` 和 `Service`
- **不标注** `@RestController`，不暴露接口
- **不进行** 权限校验和日志记录

**标准模板**：
```java
package com.han.system.controller.base;

import com.han.common.web.controller.BaseController;
import com.han.system.domain.query.SysUserQuery;
import com.han.system.domain.dto.SysUserDto;
import com.han.system.service.ISysUserService;

public class BSysUserController extends BaseController<SysUserQuery, SysUserDto, ISysUserService> {
    
    @Override
    protected String getNodeName() {
        return "用户管理";
    }
}
```

### 2.3 A 层（Admin Controller）规范

**职责**：面向 UI 管理系统，处理 HTTP 请求

**强制要求**：
- 继承 `B` 层
- 标注 `@RestController("唯一Bean名称")` 和 `@RequestMapping("/admin/xxx")`
- 标注管理端专属权限 `@AdminAuth`
- 在具体方法上添加 `@PreAuthorize` 和 `@Log`

**标准模板**：
```java
package com.han.system.controller.admin;

import com.han.common.core.domain.R;
import com.han.common.security.annotation.AdminAuth;
import com.han.common.log.annotation.OperLog;
import com.han.common.core.enums.BusinessType;
import com.han.system.controller.base.BSysUserController;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@AdminAuth
@RestController("adminSysUserController")  // 必须指定唯一Bean名称
@RequestMapping("/admin/user")
public class ASysUserController extends BSysUserController {
    
    @Override
    @GetMapping("/list")
    @PreAuthorize("@ss.hasAuthority(@Auth.SYS_USER_LIST)")
    @OperLog(title = "用户管理", businessType = BusinessType.QUERY)
    public R<PageResult<SysUserDto>> list(SysUserQuery query) {
        return super.list(query);
    }
    
    @Override
    @PostMapping("/add")
    @PreAuthorize("@ss.hasAuthority(@Auth.SYS_USER_ADD)")
    @OperLog(title = "用户管理", businessType = BusinessType.INSERT)
    public R<Void> add(@RequestBody SysUserDto dto) {
        return super.add(dto);
    }
}
```

### 2.4 I 层（Inner Controller）规范

**职责**：面向微服务内部 RPC 调用

**强制要求**：
- 继承 `B` 层
- 标注 `@RequestMapping("/inner/xxx")`
- 标注内部认证注解 `@InnerAuth`
- 通常不记录业务审计日志

**标准模板**：
```java
package com.han.system.controller.inner;

import com.han.common.core.domain.R;
import com.han.common.security.annotation.InnerAuth;
import com.han.system.controller.base.BSysUserController;
import org.springframework.web.bind.annotation.*;

@InnerAuth
@RestController("innerSysUserController")
@RequestMapping("/inner/user")
public class ISysUserController extends BSysUserController {
    
    @GetMapping("/{id}")
    public R<SysUserDto> getInfoInner(@PathVariable Long id) {
        return R.ok(baseService.selectById(id));
    }
}
```

### 2.5 强制性规则（违反将拒绝合并）

| 规则编号 | 规则内容 | 违规后果 |
|---------|---------|---------|
| **A-001** | B 层**禁止**标注 `@RestController`、`@RequestMapping` | 拒绝合并 |
| **A-002** | A 层所有 Override 方法**必须**添加 `@PreAuthorize` | 安全漏洞，拒绝合并 |
| **A-003** | I 层所有公开方法**必须**添加 `@InnerAuth` | 安全漏洞，拒绝合并 |
| **A-004** | A/I 层必须指定**唯一** Bean 名称 | 启动失败 |
| **A-005** | 禁止在 Service 层直接注入其他模块的 Service | 架构违规 |

---

## 3. 代码编写规范

### 3.1 包结构规范

**强制性目录结构**：
```
com.han.<module>
├── controller
│   ├── admin          # A 层控制器
│   ├── inner          # I 层控制器
│   └── base           # B 层控制器
├── domain
│   ├── po             # 持久化对象
│   ├── dto            # 数据传输对象
│   └── query          # 查询对象
├── service
│   ├── I<Module>Service.java
│   └── impl
│       └── <Module>ServiceImpl.java
├── mapper             # MyBatis Mapper 接口
└── converter          # MapStruct 转换器
```

### 3.2 命名规范

#### 3.2.1 类命名规范

| 类型 | 命名规则 | 示例 |
|------|---------|------|
| PO 类 | `<实体名>Po` | `SysUserPo` |
| DTO 类 | `<实体名>Dto` | `SysUserDto` |
| Query 类 | `<实体名>Query` | `SysUserQuery` |
| Controller (B 层) | `B<实体名>Controller` | `BSysUserController` |
| Controller (A 层) | `A<实体名>Controller` | `ASysUserController` |
| Controller (I 层) | `I<实体名>Controller` | `ISysUserController` |
| Service 接口 | `I<实体名>Service` | `ISysUserService` |
| Service 实现 | `<实体名>ServiceImpl` | `SysUserServiceImpl` |
| Mapper 接口 | `<实体名>Mapper` | `SysUserMapper` |
| MapStruct 转换器 | `<实体名>Converter` | `SysUserConverter` |

#### 3.2.2 方法命名规范

| 操作类型 | 方法命名规则 | 示例 |
|---------|------------|------|
| 查询单个 | `select<实体>ById` | `selectUserById(Long id)` |
| 查询列表 | `select<实体>List` | `selectUserList(SysUserQuery query)` |
| 分页查询 | `select<实体>ListScope` | `selectUserListScope(SysUserQuery query)` |
| 新增 | `insert<实体>` | `insertUser(SysUserDto dto)` |
| 修改 | `update<实体>` | `updateUser(SysUserDto dto)` |
| 删除 | `delete<实体>ById` | `deleteUserById(Long id)` |
| 批量删除 | `delete<实体>ByIds` | `deleteUserByIds(List<Long> ids)` |

### 3.3 注解使用规范

#### 3.3.1 Jackson 注解

```java
import com.fasterxml.jackson.annotation.*;

public class SysUserDto {
    @JsonProperty("user_name")
    private String userName;
    
    @JsonIgnore
    private String password;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;
    
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String remark;
}
```

#### 3.3.2 验证注解

```java
import jakarta.validation.constraints.*;

public class SysUserDto {
    @NotBlank(message = "用户名不能为空")
    @Size(min = 2, max = 20, message = "用户名长度必须在 2-20 之间")
    private String userName;
    
    @NotNull(message = "部门 ID 不能为空")
    private Long deptId;
    
    @Email(message = "邮箱格式不正确")
    private String email;
}
```

### 3.4 异常处理规范

#### 3.4.1 统一异常定义

```java
public class ServiceException extends RuntimeException {
    
    private Integer code;
    
    public ServiceException(String message) {
        super(message);
        this.code = 500;
    }
    
    public ServiceException(String message, Integer code) {
        super(message);
        this.code = code;
    }
}
```

#### 3.4.2 异常抛出规范

```java
if (user == null) {
    throw new ServiceException("用户不存在");
}

if (dept.getStatus() == 0) {
    throw new ServiceException("部门已停用，无法添加用户", 400);
}
```

---

## 4. API 接口规范

### 4.1 HTTP 方法约束（强制）

**规则**：
- ✅ **允许使用** `GET` 和 `POST`
- ❌ **禁止使用** `PUT`、`DELETE`、`PATCH` 等其他 HTTP 方法

**原因**：简化 API 设计，避免代理/防火墙限制

### 4.2 标准路由表

| 操作类型 | HTTP 方法 | 路由格式 | 示例 |
|----------|----------|---------|------|
| **查询列表** | `GET` | `/admin/<resource>/list` | `GET /admin/user/list` |
| **查询详情** | `GET` | `/admin/<resource>/{id}` | `GET /admin/user/1` |
| **新增** | `POST` | `/admin/<resource>/add` | `POST /admin/user/add` |
| **修改** | `POST` | `/admin/<resource>/edit` | `POST /admin/user/edit` |
| **删除单个** | `POST` | `/admin/<resource>/remove/{id}` | `POST /admin/user/remove/1` |
| **批量删除** | `POST` | `/admin/<resource>/remove` | `POST /admin/user/remove` |

### 4.3 Controller 实现示例

```java
@AdminAuth
@RestController("adminSysUserController")
@RequestMapping("/admin/user")
public class ASysUserController extends BSysUserController {
    
    @GetMapping("/list")
    @PreAuthorize("@ss.hasAuthority(@Auth.SYS_USER_LIST)")
    public R<PageResult<SysUserDto>> list(SysUserQuery query) {
        return super.list(query);
    }
    
    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasAuthority(@Auth.SYS_USER_LIST)")
    public R<SysUserDto> getById(@PathVariable Long id) {
        return R.ok(baseService.selectById(id));
    }
    
    @PostMapping("/add")
    @PreAuthorize("@ss.hasAuthority(@Auth.SYS_USER_ADD)")
    public R<Void> add(@RequestBody SysUserDto dto) {
        return super.add(dto);
    }
    
    @PostMapping("/edit")
    @PreAuthorize("@ss.hasAuthority(@Auth.SYS_USER_EDIT)")
    public R<Void> edit(@RequestBody SysUserDto dto) {
        return super.edit(dto);
    }
    
    @PostMapping("/remove/{id}")
    @PreAuthorize("@ss.hasAuthority(@Auth.SYS_USER_DELETE)")
    public R<Void> remove(@PathVariable Long id) {
        return super.remove(id);
    }
}
```

---

## 5. 安全规范

### 5.1 权限控制规范

#### 5.1.1 权限常量定义

```java
public class Auth {
    public static final String SYS_USER_LIST = "system:user:list";
    public static final String SYS_USER_ADD = "system:user:add";
    public static final String SYS_USER_EDIT = "system:user:edit";
    public static final String SYS_USER_DELETE = "system:user:delete";
}
```

#### 5.1.2 权限注解使用

```java
@PostMapping("/batch")
@PreAuthorize("@ss.hasAuthority(@Auth.SYS_USER_DELETE)")
public R<Void> batchRemove(@RequestBody List<Long> ids) {
    return super.batchRemove(ids);
}
```

### 5.2 敏感数据处理规范

#### 5.2.1 密码加密

```java
import at.favre.lib.crypto.bcrypt.BCrypt;

public class PasswordUtils {
    
    public static String encrypt(String plainPassword) {
        return BCrypt.withDefaults().hashToString(12, plainPassword.toCharArray());
    }
    
    public static boolean verify(String plainPassword, String hashedPassword) {
        BCrypt.Result result = BCrypt.verifyer().verify(plainPassword.toCharArray(), hashedPassword);
        return result.verified;
    }
}
```

#### 5.2.2 敏感字段脱敏

```java
public class SysUserDto {
    @JsonSerialize(using = PhoneSensitiveSerializer.class)
    private String mobile;
    
    @JsonSerialize(using = EmailSensitiveSerializer.class)
    private String email;
}
```

---

## 6. 数据传输规范

### 6.1 MapStruct 转换规范

```java
@Mapper(componentModel = "spring")
public interface SysUserConverter {
    
    @Mapping(source = "userId", target = "id")
    SysUserDto toDto(SysUserPo po);
    
    List<SysUserDto> toDtoList(List<SysUserPo> poList);
}
```

### 6.2 分页返回规范

```java
@GetMapping("/list")
@PreAuthorize("@ss.hasAuthority(@Auth.SYS_USER_LIST)")
public R<PageResult<SysUserDto>> list(SysUserQuery query) {
    startPage();
    List<SysUserDto> list = baseService.selectListScope(query);
    return R.ok(getDataTable(list));
}
```

**返回格式**：
```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "total": 100,
    "rows": [
      {
        "userId": 1,
        "userName": "admin",
        "deptName": "总公司"
      }
    ]
  }
}
```

---

# 第二部分：构建部署规则

## 1. 环境准备规则

### 1.1 必需环境

| 软件 | 版本 | 下载地址 | 用途 |
|------|------|----------|------|
| JDK | 21+ | https://adoptium.net/ | Java 运行环境 |
| Maven | 3.9+ | https://maven.apache.org/ | 项目构建 |
| Docker | 20.10+ | https://www.docker.com/ | 容器化部署 |
| PostgreSQL | 18.1 | https://www.postgresql.org/ | 数据库 |
| Redis | 7 | https://redis.io/ | 缓存 |
| Nacos | 3.1+ | https://nacos.io/ | 服务注册/配置 |

### 1.2 推荐工具

| 工具 | 用途 |
|------|------|
| IntelliJ IDEA 2024+ | 开发 IDE |
| Navicat / DBeaver | 数据库工具 |
| Apifox / Postman | API 调试 |

### 1.3 IDEA 必装插件

| 插件名称 | 用途 |
|---------|------|
| Lombok | 简化 POJO 代码 |
| MapStruct Support | MapStruct 语法支持 |
| Maven Helper | Maven 依赖分析 |
| Alibaba Java Coding Guidelines | 阿里巴巴代码规范 |

---

## 2. Maven 构建规则

### 2.1 构建命令规范

#### 2.1.1 完整构建（推荐）

```bash
# 清理并编译整个项目
mvn clean install -DskipTests

# 构建指定模块
mvn clean install -DskipTests -pl han-modules/han-system -am
```

#### 2.1.2 跳过测试构建（快速）

```bash
mvn clean package -DskipTests
```

#### 2.1.3 包含测试构建（严格）

```bash
mvn clean package
```

### 2.2 构建输出规范

**JAR 包命名规则**：
- 格式：`{服务名}-{版本}.jar`
- 示例：`han-gateway-1.0.0.jar`

**输出位置**：
- 每个模块的 `target/` 目录

### 2.3 依赖管理规范

#### 2.3.1 版本管理

所有依赖版本在 `pom.xml` 的 `<properties>` 中统一管理：

```xml
<properties>
    <java.version>21</java.version>
    <spring-boot.version>4.1.0</spring-boot.version>
    <spring-cloud.version>2025.1.2</spring-cloud.version>
    <mybatis-plus.version>3.5.9</mybatis-plus.version>
</properties>
```

#### 2.3.2 依赖冲突检查

```bash
# 检查依赖冲突
mvn dependency:tree -Dverbose

# 分析依赖
mvn dependency:analyze
```

---

## 3. Docker 部署规则

### 3.1 部署方案选择

| 规模 | 适用场景 | 硬件要求 | 中间件组合 | 部署文件 |
|------|----------|----------|-----------|----------|
| **小型（极简版）** | 个人开发/学习/演示 | 2核4GB | PostgreSQL + Redis + Nacos | `docker-compose-small.yml` |
| **中型（标准版）** | 小团队/测试环境 | 4核8GB | + RabbitMQ | `docker-compose.yml` |
| **大型（企业版）** | 生产环境/大规模 | 8核16GB+ | + Kafka + Elasticsearch | Kubernetes 部署 |

### 3.2 小型部署（推荐入门）

**适合**: 个人学习、功能演示、快速验证

#### 启动命令

```bash
docker-compose -f docker-compose-small.yml up -d
```

#### 包含服务

- ✅ PostgreSQL (数据库)
- ✅ Redis (缓存 + 分布式锁)
- ✅ Nacos (服务注册 + 配置中心)
- ✅ Gateway (网关)
- ✅ Auth (认证服务)
- ✅ System (系统管理)
- ✅ Job (JobFlow 任务调度)

#### 访问地址

- **Nacos**: http://localhost:8848/nacos (han/han@2026)
- **网关**: http://localhost:8080

### 3.3 中型部署（推荐团队）

**适合**: 小团队开发、测试环境、完整功能验证

#### 启动命令

```bash
docker-compose up -d
```

#### 额外包含服务

- ✅ RabbitMQ (异步消息队列)
- ✅ Tenant (租户管理)
- ✅ Workflow (工作流)
- ✅ Open (开放平台)
- ✅ Gen (代码生成)
- ✅ File (文件服务)

#### 访问额外服务

- **RabbitMQ**: http://localhost:15672 (guest/guest)

### 3.4 Docker Compose 配置规范

#### 3.4.1 服务命名规范

- 格式：`han-{服务名}`
- 示例：`han-gateway`、`han-auth`

#### 3.4.2 网络配置规范

```yaml
networks:
  han-network:
    driver: bridge
```

所有服务必须使用同一个网络 `han-network`。

#### 3.4.3 数据卷配置规范

```yaml
volumes:
  postgres_data:
  redis_data:
  nacos_data:
```

#### 3.4.4 环境变量规范

```yaml
environment:
  SPRING_PROFILES_ACTIVE: docker
  NACOS_SERVER_ADDR: nacos:8848
```

### 3.5 Dockerfile 规范

#### 3.5.1 基础镜像规范

```dockerfile
FROM openjdk:21-jdk-slim
LABEL maintainer="Han Team <support@han.com>"
```

#### 3.5.2 构建步骤规范

```dockerfile
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

#### 3.5.3 优化建议

- 使用多阶段构建减小镜像体积
- 使用 `.dockerignore` 排除不必要文件
- 使用非 root 用户运行应用

---

## 4. Kubernetes 部署规则

### 4.1 前置条件

- Kubernetes 集群 (v1.25+)
- kubectl 命令行工具
- Helm 3.0+

### 4.2 部署步骤

#### 4.2.1 创建命名空间

```bash
kubectl create namespace han-cloud
```

#### 4.2.2 创建配置

```bash
kubectl apply -f k8s/configmap.yaml -n han-cloud
kubectl apply -f k8s/secret.yaml -n han-cloud
```

#### 4.2.3 部署中间件

```bash
# 部署 PostgreSQL
helm install postgres bitnami/postgresql -n han-cloud

# 部署 Redis
helm install redis bitnami/redis -n han-cloud

# 部署 Nacos
helm install nacos nacos/nacos -n han-cloud
```

#### 4.2.4 部署业务服务

```bash
kubectl apply -f k8s/gateway-deployment.yaml -n han-cloud
kubectl apply -f k8s/auth-deployment.yaml -n han-cloud
kubectl apply -f k8s/system-deployment.yaml -n han-cloud
```

### 4.3 Deployment 配置规范

#### 4.3.1 副本数规范

| 环境 | 副本数 |
|------|--------|
| 开发 | 1 |
| 测试 | 2 |
| 生产 | 3+ |

#### 4.3.2 资源限制规范

```yaml
resources:
  requests:
    memory: "512Mi"
    cpu: "500m"
  limits:
    memory: "1Gi"
    cpu: "1000m"
```

#### 4.3.3 健康检查规范

```yaml
livenessProbe:
  httpGet:
    path: /actuator/health
    port: 8080
  initialDelaySeconds: 60
  periodSeconds: 10

readinessProbe:
  httpGet:
    path: /actuator/health
    port: 8080
  initialDelaySeconds: 30
  periodSeconds: 5
```

### 4.4 Service 配置规范

```yaml
spec:
  selector:
    app: han-gateway
  ports:
  - protocol: TCP
    port: 8080
    targetPort: 8080
  type: ClusterIP
```

### 4.5 运维命令规范

```bash
# 查看 Pod
kubectl get pods -n han-cloud

# 查看 Service
kubectl get svc -n han-cloud

# 查看日志
kubectl logs -f <pod-name> -n han-cloud

# 扩容
kubectl scale deployment han-gateway --replicas=5 -n han-cloud

# 更新镜像
kubectl set image deployment/han-gateway gateway=han/gateway:v2.0 -n han-cloud

# 重启服务
kubectl rollout restart deployment/han-gateway -n han-cloud

# 回滚
kubectl rollout undo deployment/han-gateway -n han-cloud
```

---

## 5. 运维管理规则

### 5.1 健康检查规范

#### 5.1.1 服务健康检查

```bash
# 检查服务健康状态
curl http://localhost:8080/actuator/health

# 检查详细信息
curl http://localhost:8080/actuator/info
```

#### 5.1.2 健康检查端点规范

所有服务必须暴露 `/actuator/health` 端点。

### 5.2 日志管理规范

#### 5.2.1 Docker Compose 日志

```bash
# 查看所有服务日志
docker-compose logs -f

# 查看特定服务日志
docker-compose logs -f gateway

# 限制日志行数
docker-compose logs --tail=100 gateway
```

#### 5.2.2 Kubernetes 日志

```bash
# 查看 Pod 日志
kubectl logs -f <pod-name> -n han-cloud

# 查看前 N 行日志
kubectl logs --tail=100 <pod-name> -n han-cloud

# 查看多个副本日志
kubectl logs -l app=han-gateway -n han-cloud
```

### 5.3 数据备份规范

#### 5.3.1 PostgreSQL 备份

```bash
# 备份数据库
docker exec han-postgres pg_dump -U han han > backup_$(date +%Y%m%d).sql

# 恢复数据库
docker exec -i han-postgres psql -U han han < backup_20260129.sql
```

#### 5.3.2 Redis 备份

```bash
# 触发 RDB 快照
docker exec han-redis redis-cli BGSAVE

# 复制备份文件
docker cp han-redis:/data/dump.rdb ./redis_backup_$(date +%Y%m%d).rdb
```

### 5.4 监控告警规范

#### 5.4.1 Prometheus 指标规范

所有服务必须暴露 Prometheus 指标端点：`/actuator/prometheus`

#### 5.4.2 关键指标

- 请求成功率
- 请求响应时间
- JVM 内存使用
- 线程池状态
- 数据库连接池状态

---

# 第三部分：测试规则

## 1. 单元测试规范

### 1.1 测试文件位置

- 位置：对应模块的 `src/test/java/` 目录
- 命名规范：`{被测试类名}Test.java`

### 1.2 测试框架规范

- 使用 JUnit 5
- 使用 Mockito 进行 Mock
- 使用 AssertJ 进行断言

### 1.3 测试类模板

```java
@SpringBootTest
@Slf4j
class XxxServiceTest {

    @Autowired
    private XxxService xxxService;

    @Test
    @DisplayName("测试方法名称")
    void testMethodName() {
    }
}
```

### 1.4 测试覆盖率要求

| 测试类型 | 最低覆盖率 | 推荐覆盖率 |
|---------|-----------|-----------|
| **业务逻辑测试** | 70% | 80%+ |
| **核心模块测试** | 80% | 90%+ |
| **工具类测试** | 90% | 100% |

### 1.5 测试方法规范

#### 1.5.1 命名规范

- 格式：`test{被测试方法名}_{场景}_{预期结果}`
- 示例：`testSelectUserById_用户存在_返回用户信息`

#### 1.5.2 测试结构规范

使用 Given-When-Then 结构：

```java
@Test
@DisplayName("根据 ID 查询用户 - 用户存在 - 返回用户信息")
void testSelectUserById_用户存在_返回用户信息() {
    // Given - 准备测试数据
    Long userId = 1L;
    
    // When - 执行测试方法
    SysUserDto user = userService.selectById(userId);
    
    // Then - 验证结果
    assertNotNull(user);
    assertEquals(userId, user.getUserId());
    assertEquals("admin", user.getUserName());
}
```

---

## 2. 接口测试规范

### 2.1 测试工具

- 推荐使用 `MockMvc` 进行接口测试
- 可选使用 Postman/Apifox 进行手动测试

### 2.2 MockMvc 测试模板

```java
@SpringBootTest
@AutoConfigureMockMvc
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("查询用户列表 - 成功返回")
    void testList_Success() throws Exception {
        mockMvc.perform(get("/admin/user/list")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").exists());
    }
}
```

### 2.3 接口测试覆盖率要求

| 测试类型 | 最低覆盖率 |
|---------|-----------|
| **正常流程测试** | 100% |
| **异常流程测试** | 80% |
| **边界条件测试** | 70% |

---

## 3. 架构测试规范

### 3.1 测试框架

使用 ArchUnit 进行架构测试：

```xml
<dependency>
    <groupId>com.tngtech.archunit</groupId>
    <artifactId>archunit-junit5</artifactId>
    <version>1.3.0</version>
    <scope>test</scope>
</dependency>
```

### 3.2 架构规则测试

#### 3.2.1 Controller 命名规范测试

```java
@Test
void controllerClassesShouldEndWithController() {
    ArchRule rule = classes()
        .that().resideInAPackage("..controller..")
        .should().haveSimpleNameEndingWith("Controller");
    
    rule.check(classes);
}
```

#### 3.2.2 AIB 架构测试

```java
@Test
void baseControllerMustNotHaveRestController() {
    ArchRule rule = classes()
        .that().haveSimpleNameStartingWith("B")
        .and().haveSimpleNameEndingWith("Controller")
        .should().notBeAnnotatedWith(RestController.class);
    
    rule.check(classes);
}
```

#### 3.2.3 依赖规则测试

```java
@Test
void controllersShouldNotAccessRepositoriesDirectly() {
    ArchRule rule = noClasses()
        .that().resideInAPackage("..controller..")
        .should().dependOnClassesThat().resideInAPackage("..mapper..");
    
    rule.check(classes);
}
```

---

## 4. 测试覆盖率要求

### 4.1 整体覆盖率要求

| 模块类型 | 最低覆盖率 | 推荐覆盖率 |
|---------|-----------|-----------|
| **核心业务模块** | 70% | 80%+ |
| **公共模块** | 80% | 90%+ |
| **工具类模块** | 90% | 100% |

### 4.2 代码覆盖率报告

#### 4.2.1 生成覆盖率报告

```bash
# 使用 JaCoCo 生成覆盖率报告
mvn clean test jacoco:report
```

#### 4.2.2 报告位置

- HTML 报告：`target/site/jacoco/index.html`

### 4.3 测试执行规范

#### 4.3.1 本地开发测试

```bash
# 执行所有测试
mvn test

# 执行指定模块测试
mvn test -pl han-modules/han-system
```

#### 4.3.2 CI/CD 测试

```bash
# 跳过测试（构建阶段）
mvn clean package -DskipTests

# 执行测试（测试阶段）
mvn test
```

### 4.4 测试规范检查清单

在提交代码前，必须确认：

- [ ] 是否编写了单元测试？
- [ ] 测试覆盖率是否达到 70% 以上？
- [ ] 是否通过了 ArchUnit 架构测试？
- [ ] 是否通过了所有集成测试？
- [ ] 是否更新了测试用例文档？

---

# 附录

## A. 禁止事项清单

### A.1 技术禁令

| 禁止项 | 原因 | 替代方案 |
|--------|------|----------|
| ❌ Fastjson/Gson | 安全漏洞、序列化不一致 | ✅ Jackson |
| ❌ Feign | 已弃用，与 Spring 6 不兼容 | ✅ HttpExchange |
| ❌ BeanUtils.copyProperties | 反射性能差、类型不安全 | ✅ MapStruct |
| ❌ RestTemplate | 已过时 | ✅ HttpExchange |
| ❌ @Autowired 字段注入（业务代码） | 不利于单元测试（测试类例外） | ✅ 构造器注入 |
| ❌ new Date() | 线程不安全 | ✅ LocalDateTime |
| ❌ SimpleDateFormat | 线程不安全 | ✅ DateTimeFormatter |
| ❌ 直接引入 Hutool | 难以统一管理 | ✅ 通过 han-common-core 工具类 |
| ❌ PUT/DELETE/PATCH 方法 | 代理/防火墙限制 | ✅ 仅使用 GET/POST |

### A.2 架构禁令

| 禁止项 | 原因 | 正确做法 |
|--------|------|----------|
| ❌ B 层标注 @RestController | 破坏架构设计 | 仅在 A/I 层标注 |
| ❌ Service 跨模块直接注入 | 高耦合 | 使用 HttpExchange 或事件驱动 |
| ❌ Controller 直接操作 Mapper | 绕过业务逻辑层 | 必须通过 Service |
| ❌ 在 Query 中重写 PO 字段类型 | 序列化混乱 | 使用不同字段名 |
| ❌ 硬编码权限标识 | 难以维护 | 使用 Auth 常量类 |

### A.3 安全禁令

| 禁止项 | 原因 | 正确做法 |
|--------|------|----------|
| ❌ A 层方法缺少 @PreAuthorize | 安全漏洞 | 所有公开方法必须添加权限校验 |
| ❌ 明文存储密码 | 数据泄露风险 | 使用 BCrypt 加密 |
| ❌ SQL 拼接查询条件 | SQL 注入风险 | 使用 MyBatis-Plus 条件构造器 |
| ❌ 敏感信息写入日志 | 信息泄露 | 使用 @JsonIgnore 隐藏 |
| ❌ 跨域配置 `allowedOrigins("*")` | CSRF 风险 | 明确指定允许的域名 |

## B. 快速参考

### B.1 服务端口映射

| 服务 | 端口 | 说明 |
|------|------|------|
| Gateway | 8080 | 网关 API |
| Auth | 9200 | 认证服务 |
| System | 9201 | 系统管理 |
| Tenant | 9202 | 租户管理 |
| Workflow | 9203 | 工作流 |
| Job | 9204 | 任务调度 |
| Open | 9205 | 开放平台 |
| Gen | 9206 | 代码生成 |
| File | 9207 | 文件服务 |
| PostgreSQL | 5432 | 数据库 |
| Redis | 6379 | 缓存 |
| Nacos | 8848 | 注册/配置 |

### B.2 常用命令速查

```bash
# Maven 构建
mvn clean install -DskipTests

# Docker 启动小型环境
docker-compose -f docker-compose-small.yml up -d

# Docker 查看日志
docker-compose logs -f
```bash
# Kubernetes 查看 Pod
kubectl get pods -n han-cloud

# 运行测试
mvn test

# 生成覆盖率报告
mvn clean test jacoco:report
```

---

**版本信息**：
- **当前版本**: 1.0.0
- **更新日期**: 2026-01-29
- **适用范围**: han Cloud 全部代码仓库

**© han Cloud Team - 项目规则文档**
