# XuMan Cloud 开发者快速上手指南

## 环境准备

### 必需环境

| 软件 | 版本 | 下载地址 |
|------|------|----------|
| JDK | 21+ | https://adoptium.net/ |
| Maven | 3.9+ | https://maven.apache.org/ |
| MySQL | 8.0+ | https://dev.mysql.com/downloads/ |
| Redis | 6.0+ | https://redis.io/download/ |
| Nacos | 2.3+ | https://nacos.io/ |
| Node.js | 18+ | https://nodejs.org/ (前端开发) |

### 推荐工具

- IDE: IntelliJ IDEA 2024+
- 数据库工具: Navicat / DBeaver
- API调试: Apifox / Postman

---

## 快速启动

### 1. 克隆项目

```bash
git clone https://github.com/your-org/xuman-cloud.git
cd xuman-cloud
```

### 2. 初始化数据库

```sql
-- 创建数据库
CREATE DATABASE xuman DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

-- 按顺序执行SQL脚本
-- 1. sql/xuman_system.sql    (系统表结构)
-- 2. sql/xuman_data.sql      (初始化数据)
-- 3. sql/xuman_job.sql       (定时任务表)
-- 4. sql/xuman_workflow.sql  (工作流表)
-- 5. sql/xuman_open.sql      (开放平台表)
-- 6. sql/xuman_gen.sql       (代码生成表)
```

### 3. 启动中间件

```bash
# 启动Nacos (单机模式)
sh nacos/bin/startup.sh -m standalone

# 启动Redis
redis-server
```

### 4. 修改配置

修改 `xuman-gateway/src/main/resources/bootstrap.yml`:
```yaml
spring:
  cloud:
    nacos:
      discovery:
        server-addr: 127.0.0.1:8848
      config:
        server-addr: 127.0.0.1:8848
```

在Nacos控制台导入配置文件或手动创建配置。

### 5. 启动服务

```bash
# 编译项目
mvn clean install -DskipTests

# 按顺序启动服务
# 1. xuman-gateway
# 2. xuman-auth
# 3. xuman-system
# 4. 其他业务模块
```

### 6. 启动前端

```bash
cd xuman-ui
npm install
npm run dev
```

访问 http://localhost:80 ，默认账号: admin / admin123

---

## 开发规范

### 项目分层

```
Controller → Service → Mapper → Database
     ↓          ↓
    DTO        Entity
     ↓
    VO (响应给前端)
```

### 命名规范

| 类型 | 规范 | 示例 |
|------|------|------|
| Entity | 驼峰，与表名对应 | `SysUser`, `OpenApp` |
| DTO | 请求参数传输对象 | `UserDTO`, `UserQueryDTO` |
| VO | 响应视图对象 | `UserVO`, `UserDetailVO` |
| Service | 接口+Impl实现 | `UserService` / `UserServiceImpl` |
| Controller | 模块名+Controller | `UserController` |
| Mapper | 实体名+Mapper | `SysUserMapper` |

### 返回值规范

统一使用 `R<T>` 包装响应：

```java
// 成功
return R.ok(data);
return R.ok();

// 失败
return R.fail("错误信息");
return R.fail(500, "错误信息");
```

### 分页查询

```java
// Controller
@GetMapping("/list")
public R<PageResult<UserVO>> list(UserQueryDTO dto) {
    return R.ok(userService.listUser(dto));
}

// Service
public PageResult<UserVO> listUser(UserQueryDTO dto) {
    Page<SysUser> page = new Page<>(dto.getPageNum(), dto.getPageSize());
    // 查询...
    return PageResult.build(page, UserVO.class);
}
```

---

## 新增业务模块

### 1. 创建模块

在 `xuman-modules` 下创建新模块：

```
xuman-modules/xuman-xxx
├── pom.xml
└── src/main/java/com/xuman/xxx
    ├── XumanXxxApplication.java
    ├── controller/
    ├── service/
    │   └── impl/
    ├── mapper/
    └── domain/
        ├── entity/
        ├── dto/
        └── vo/
```

### 2. pom.xml 配置

```xml
<parent>
    <groupId>com.xuman</groupId>
    <artifactId>xuman-modules</artifactId>
    <version>1.0.0</version>
</parent>

<artifactId>xuman-xxx</artifactId>

<dependencies>
    <!-- 公共Web依赖 -->
    <dependency>
        <groupId>com.xuman</groupId>
        <artifactId>xuman-common-web</artifactId>
    </dependency>
    <!-- MyBatis -->
    <dependency>
        <groupId>com.xuman</groupId>
        <artifactId>xuman-common-mybatis</artifactId>
    </dependency>
    <!-- 安全模块 -->
    <dependency>
        <groupId>com.xuman</groupId>
        <artifactId>xuman-common-security</artifactId>
    </dependency>
</dependencies>
```

### 3. 启动类

```java
@SpringBootApplication
@EnableDiscoveryClient
public class XumanXxxApplication {
    public static void main(String[] args) {
        SpringApplication.run(XumanXxxApplication.class, args);
    }
}
```

### 4. 配置文件

`application.yml`:
```yaml
server:
  port: 9210

spring:
  application:
    name: xuman-xxx
  datasource:
    url: jdbc:mysql://localhost:3306/xuman?useUnicode=true&characterEncoding=utf8
    username: root
    password: root
```

---

## 工作流开发

### 1. 部署流程定义

```java
@Autowired
private ProcessDefinitionService processDefinitionService;

// 通过XML部署
processDefinitionService.deployByXml("请假流程", "OA", bpmnXml);

// 通过文件流部署
processDefinitionService.deploy("请假流程", "OA", inputStream);
```

### 2. 启动流程实例

```java
@Autowired
private ProcessInstanceService processInstanceService;

ProcessStartDTO dto = new ProcessStartDTO();
dto.setProcessDefinitionKey("leave_process");
dto.setBusinessKey("LEAVE_20240101001");
dto.setVariables(Map.of(
    "applicant", "张三",
    "days", 3,
    "reason", "年假"
));

ProcessInstanceVO instance = processInstanceService.startProcess(dto);
```

### 3. 完成任务

```java
TaskCompleteDTO dto = new TaskCompleteDTO();
dto.setTaskId("12345");
dto.setVariables(Map.of("approved", true));
dto.setComment("同意");

processInstanceService.completeTask(dto);
```

### 4. 查询待办任务

```java
TaskQueryDTO dto = new TaskQueryDTO();
dto.setAssignee("zhangsan");

PageResult<TaskVO> tasks = processInstanceService.listTodoTasks(dto);
```

---

## 定时任务开发

### 1. 创建任务Handler

```java
@Slf4j
@Component("myTask")  // Bean名称，用于调用目标配置
public class MyTaskHandler {

    /**
     * 无参任务
     * 调用目标: myTask.execute
     */
    public void execute() {
        log.info("开始执行任务...");
        // 业务逻辑
        log.info("任务执行完成");
    }

    /**
     * 带参任务
     * 调用目标: myTask.executeWithParam(参数值)
     */
    public void executeWithParam(String param) {
        log.info("执行带参任务, 参数: {}", param);
        // 业务逻辑
    }
}
```

### 2. 通过管理界面配置任务

在前端管理界面创建任务：
- **任务名称**: 数据同步任务
- **任务组名**: SYSTEM
- **调用目标**: myTask.execute 或 myTask.executeWithParam(test)
- **Cron表达式**: 0 0/5 * * * ? (每5分钟执行)
- **执行策略**: 放弃执行
- **是否并发**: 禁止

### 3. 通过API创建任务

```java
@Autowired
private SysJobService jobService;

public void createJob() {
    JobDTO dto = new JobDTO();
    dto.setJobName("数据同步任务");
    dto.setJobGroup("SYSTEM");
    dto.setInvokeTarget("myTask.syncData");
    dto.setCronExpression("0 0 2 * * ?");  // 每天凌晨2点
    dto.setMisfirePolicy("3");  // 放弃执行
    dto.setConcurrent("1");     // 禁止并发
    dto.setStatus("0");         // 正常状态
    
    jobService.createJob(dto);
}
```

### 4. 常用Cron表达式

| 表达式 | 说明 |
|--------|------|
| 0 0/5 * * * ? | 每5分钟执行 |
| 0 0 * * * ? | 每小时执行 |
| 0 0 2 * * ? | 每天凌晨2点 |
| 0 0 0 1 * ? | 每月1号零点 |
| 0 0 0 ? * MON | 每周一零点 |

---

## 常用代码片段

### 获取当前用户

```java
import com.xuman.common.security.context.SecurityContextHolder;

// 获取用户ID
Long userId = SecurityContextHolder.getUserId();

// 获取用户名
String username = SecurityContextHolder.getUsername();

// 获取租户ID
Long tenantId = SecurityContextHolder.getTenantId();

// 检查是否登录
boolean isLogin = SecurityContextHolder.isLogin();
```

### 权限注解

```java
// 需要登录
@RequiresLogin

// 需要特定权限
@RequiresPermissions("system:user:add")

// 需要特定角色
@RequiresRoles("admin")
```

### 日志记录

```java
// 操作日志注解
@Log(title = "用户管理", businessType = BusinessType.INSERT)
@PostMapping
public R<Void> add(@RequestBody UserDTO dto) {
    // ...
}
```

### 缓存使用

```java
@Cacheable(value = "user", key = "#id")
public SysUser getById(Long id) {
    return userMapper.selectById(id);
}

@CacheEvict(value = "user", key = "#id")
public void deleteById(Long id) {
    userMapper.deleteById(id);
}
```

### 分布式锁

```java
@Autowired
private RedissonClient redissonClient;

public void doWithLock() {
    RLock lock = redissonClient.getLock("my_lock_key");
    try {
        if (lock.tryLock(10, 30, TimeUnit.SECONDS)) {
            // 业务逻辑
        }
    } finally {
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}
```

---

## 调试技巧

### 接口文档

启动服务后访问:
- Knife4j: http://localhost:8080/doc.html
- Swagger: http://localhost:8080/swagger-ui.html

### 日志级别

临时调整日志级别(Nacos配置):
```yaml
logging:
  level:
    com.xuman: debug
    org.flowable: debug
```

### SQL日志

```yaml
mybatis-plus:
  configuration:
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
```

---

## 常见问题

### Q: 启动报错 "Table doesn't exist"
A: 检查是否执行了所有SQL脚本，确保数据库连接配置正确。

### Q: Nacos配置不生效
A: 确认bootstrap.yml中的namespace和group与Nacos控制台一致。

### Q: 工作流部署失败
A: 检查BPMN XML格式是否正确，可使用Flowable Modeler验证。

### Q: XXL-JOB任务不执行
A: 
1. 确认执行器已注册到Admin
2. 检查AppName是否一致
3. 检查任务状态是否为"运行中"

---

## 参考资源

- [Spring Boot 官方文档](https://docs.spring.io/spring-boot/)
- [MyBatis-Plus 官方文档](https://baomidou.com/)
- [Flowable 用户手册](https://www.flowable.com/open-source/docs/)
- [XXL-JOB 官方文档](https://www.xuxueli.com/xxl-job/)
