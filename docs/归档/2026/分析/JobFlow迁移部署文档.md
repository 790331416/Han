# JobFlow 迁移部署文档

## 概述

本次变更将 `han-job` 模块的调度框架从 **Quartz** 替换为自研的 **JobFlow** 轻量级调度框架。

### 变更范围

| 变更项 | 说明 |
|--------|------|
| 调度引擎 | Quartz → JobFlow（自研） |
| 依赖变化 | 移除 `spring-boot-starter-quartz` |
| 数据库变化 | 删除 `qrtz_*` 表，`sys_job` 新增 2 个字段 |
| 配置变化 | 移除 Quartz YAML 配置，新增 JobFlow 配置 |

---

## 一、数据库变更

### 1.1 执行 SQL 脚本

在 PostgreSQL 中执行 `sql/jobflow_migration.sql`：

```bash
psql -U han -d han -f sql/jobflow_migration.sql
```

### 1.2 变更明细

**新增字段：**

| 表名 | 字段名 | 类型 | 说明 |
|------|--------|------|------|
| sys_job | service_name | VARCHAR(100) | 执行器服务名（Nacos 注册名，远程调用时使用） |
| sys_job | handler | VARCHAR(100) | 执行器处理方法路径（远程调用时使用） |

**删除表：**

所有 `qrtz_` 前缀的 Quartz 表（共 11 张）。

---

## 二、代码变更

### 2.1 删除的文件

| 文件 | 说明 |
|------|------|
| `QuartzConfig.java` | Quartz 调度器配置 |
| `QuartzJobUtils.java` | Quartz 任务工具类 |
| `AbstractQuartzJob.java` | Quartz 抽象任务类 |
| `QuartzJobExecution.java` | 允许并发执行的任务实现 |
| `QuartzDisallowConcurrentExecution.java` | 禁止并发执行的任务实现 |
| `JobInitializer.java` | Quartz 任务初始化器 |

### 2.2 新增的文件

| 文件 | 说明 |
|------|------|
| `scheduler/JobFlowScheduler.java` | JobFlow 核心调度器 |
| `scheduler/ExecutorDiscovery.java` | Nacos 执行器发现（支持缓存降级） |
| `scheduler/JobFlowExecutorClient.java` | HTTP 远程执行器客户端 |
| `scheduler/JobFlowInitializer.java` | JobFlow 启动器 |
| `scheduler/CompensationTask.java` | 超时任务补偿 |
| `util/CronUtils.java` | Cron 工具类（兼容 Quartz 7 位格式和 `?` 字符） |

### 2.3 修改的文件

| 文件 | 变更说明 |
|------|---------|
| `pom.xml` | 移除 `spring-boot-starter-quartz` 依赖 |
| `application.yml` | 移除 Quartz 配置，新增 `jobflow.scheduler` 配置 |
| `hanJobApplication.java` | 添加 `@EnableScheduling` |
| `SysJob.java` | 新增 `serviceName`、`handler` 字段 |
| `SysJobService.java` | 移除所有 `SchedulerException` |
| `SysJobServiceImpl.java` | 使用 JobFlowScheduler 替代 Quartz Scheduler |
| `ASysJobController.java` | 移除 SchedulerException catch 块 |
| `ISysJobController.java` | 移除 SchedulerException catch 块 |
| `JobFlowMonitorController.java` | 改为 JobFlow 风格监控端点 |

---

## 三、配置变更

### 3.1 application.yml 新增配置

```yaml
# JobFlow 调度器配置
jobflow:
  scheduler:
    thread-pool-size: 20        # 任务执行线程池大小
    timeout: 300                # 任务超时时间（秒）
    max-retry: 3                # 最大重试次数
    connect-timeout: 5000       # HTTP 连接超时（毫秒）
    read-timeout: 30000         # HTTP 读取超时（毫秒）
    lock-timeout: 60            # 分布式锁超时（秒）
    compensation-enabled: true  # 是否启用补偿任务
    compensation-interval: 60000 # 补偿扫描间隔（毫秒）
    stuck-threshold: 600000     # 卡住阈值（毫秒）
    scan-interval: 10           # 调度扫描间隔（秒）
```

### 3.2 Nacos 配置（如使用 Nacos 配置中心）

需要将上述 `jobflow.scheduler` 配置同步到 Nacos 对应的配置文件中。

---

## 四、部署步骤

### 4.1 构建

```bash
# 设置 JDK 21
export JAVA_HOME=/path/to/jdk-21

# 构建 Job 模块
mvn clean package -pl han-modules/han-job -am -DskipTests
```

### 4.2 数据库迁移

```bash
# 在 95 服务器上执行 SQL
psql -U han -d han -f sql/jobflow_migration.sql
```

### 4.3 部署服务

```bash
# Docker 方式（替换旧镜像）
docker stop han-job
docker rm han-job

# 构建新镜像并启动（参考 deploy.sh）
# 或直接运行 jar
java -jar han-job-1.0.0.jar --server.port=9204
```

### 4.4 验证

1. 检查 Nacos 注册：`http://<nacos-host>:8848/nacos` → 服务列表中出现 `han-job`
2. 检查健康状态：`GET /actuator/jobflow/health`
3. 检查调度配置：`GET /actuator/jobflow/config`
4. 测试任务列表：`GET /job/list?pageNum=1&pageSize=10`
5. 测试 Cron 校验：`GET /job/checkCron?cronExpression=0 0/5 * * * ?`
6. 查看日志确认 `JobFlow 调度器已启动` 字样

---

## 五、回滚方案

如需回滚到 Quartz：

1. 恢复 `pom.xml` 中的 `spring-boot-starter-quartz` 依赖
2. 恢复被删除的 Quartz 相关 Java 类（从 Git 历史恢复）
3. 恢复 `application.yml` 中的 Quartz 配置
4. 重新创建 `qrtz_*` 表（使用 Quartz 官方 DDL 脚本）
5. 重新构建部署

---

## 六、JobFlow 架构说明

### 核心组件

```
JobFlowScheduler（核心调度器）
├── 定时扫描 DB 中 status='0' 的任务
├── CronExpression 判断是否到执行时间
├── Redis 分布式锁防止多实例重复调度
├── 本地执行（invokeTarget → 反射调用 Bean 方法）
├── 远程执行（serviceName + handler → HTTP 调用执行器）
└── TraceId 全链路追踪

ExecutorDiscovery（执行器发现）
├── 通过 Nacos DiscoveryClient 获取执行器实例
├── 轮询负载均衡
└── 本地缓存降级

CompensationTask（补偿任务）
└── 定期扫描卡住的执行记录，标记为超时
```

### 任务类型

| 类型 | 配置 | 说明 |
|------|------|------|
| 本地任务 | 仅设置 `invokeTarget` | 反射调用本地 Spring Bean 方法 |
| 远程任务 | 设置 `serviceName` + `handler` | HTTP 调用 Nacos 注册的执行器服务 |
| 分片任务 | 使用 `ShardExecutor` | 利用分片策略并行处理（已有组件复用） |
