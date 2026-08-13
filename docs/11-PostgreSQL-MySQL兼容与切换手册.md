# PostgreSQL/MySQL 兼容与切换手册

## 1. 结论与支持矩阵

PostgreSQL 保持 Han 的默认数据库。MySQL 8.4 是正式可选数据库，但只开放已经具备独立 SQL、Compose 和门禁的范围。

| 范围 | PostgreSQL | MySQL 8.4 | 说明 |
| --- | --- | --- | --- |
| 公共 MyBatis、分页、多租户 | 支持 | 支持 | 方言按 JDBC URL 自动识别 |
| 业务模块 JDBC 驱动 | 支持 | 支持 | 两种驱动随数据库模块一起构建 |
| 代码生成器元数据查询 | 支持 | 支持 | 按数据库产品名选择 mapper SQL |
| small clean 初始化 | 支持 | 支持 | 三档均已完成实库导入验证，见下方 §1.1 |
| medium/full clean 初始化 | 支持 | 支持 | 同上 |
| 存量升级 | 支持 | 支持 | `sql/upgrades/mysql/`，起算点见 §1.2，幂等已实测 |

### 1.1 实库验证证据（2026-08-13）

在 10.18.35.95 上用一次性容器（`mysql:8.4.10` 与 `postgres:18.1`，验证后已销毁，
未触碰 `/opt/han/deploy` 的正式栈）完成双库对照导入，三档均为**严格模式零错误**，
且两种数据库产出的对象与种子数量**逐项一致**：

| 档位 | 表数 | 菜单数 | 权限串数 |
| --- | --- | --- | --- |
| small | 21 | 70 | 66 |
| medium | 35 | 105 | 98 |
| full | 50 | 139 | 131 |

MySQL 侧 8 个 `del_flag` 函数式唯一索引均真实建成（`information_schema.statistics`
中 `expression IS NOT NULL` 可查），与 PostgreSQL 的部分唯一索引语义对齐。

`sql/upgrades/mysql/` 两个脚本在真实实例上连续执行两次，结果完全一致，
执行后无残留存储过程，**幂等成立**；在已是最新的 small 库上执行为空操作
（菜单与权限串数量不变），不会再注入本档不该有的菜单。

### 1.3 服务层验证与镜像前置条件（2026-08-13）

small 档已用 `deploy/small/docker-compose-mysql.yml` 拉起完整栈完成服务层回归：
登录成功、13 个业务接口全部 200、登录日志落库。详见
[测试与验收手册 §15](04-测试与验收手册.md)。

> **切换 MySQL 前必读**：服务镜像必须用 2026-08-11 之后的代码重新构建。
> 双数据库驱动是那天才加入各模块 pom 的，此前发布的镜像（如构建于 2026-07-02 的
> `han-system:latest`）只打包了 PostgreSQL 驱动，配上 `DB_URL=jdbc:mysql://...` 后
> 仍会用 PostgreSQL 驱动去连 MySQL，在 SSL 协商阶段直接失败。
> 这一条与 SQL 是否正确无关，沿用旧镜像必然启动不起来。

medium/full 档的服务层、真实浏览器页面回归、MySQL 下的任务调度执行仍未覆盖。

### 1.2 MySQL 存量升级的起算点

MySQL 支持是 2026-08-11 引入的，此前不存在任何 MySQL 库，
因此 `sql/upgrades/postgres/` 里 2026-08-11 之前的历史脚本**不回港到 MySQL**——
那些变更已经烘焙在 `*-init-mysql.sql` 里，回港属于死代码。
`sql/upgrades/mysql/` 只承载 2026-08-11 之后的增量。全新安装不需要执行升级脚本。

## 2. 为什么以前不能直接切换

历史 `han-common-datasource` 只有动态数据源 starter 和 PostgreSQL 驱动，没有被业务模块接入；公共分页配置又写死了 `DbType.POSTGRE_SQL`。同时业务模块配置、代码生成器元数据查询、正式 SQL 和 Compose 全部按 PostgreSQL 收敛。因此当时只是残留了“多库模块”的结构，并没有形成端到端双数据库能力。

本轮恢复遵循最小改造：不新增一套平行数据源框架，不改变 PostgreSQL 默认行为，只消除公共硬编码并补齐 MySQL 所需入口。

## 3. 运行时切换

不设置 `DB_URL` 时继续使用 PostgreSQL 默认连接。MySQL 连接示例：

```text
DB_URL=jdbc:mysql://mysql:3306/han?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
DB_USER=han
DB_PASSWORD=<从部署环境注入>
```

不要再设置固定 `driver-class-name`。Spring Boot 根据 JDBC URL 和 classpath 中的驱动自动选择驱动，MyBatis-Plus 同样根据 JDBC URL 识别分页方言。

## 4. 三档部署入口

每档都有一份独立的 MySQL Compose，与 PostgreSQL 版并列，互不覆盖：

| 档位 | PostgreSQL 入口 | MySQL 入口 | MySQL 初始化脚本 | MySQL 宿主机端口 |
| --- | --- | --- | --- | --- |
| small | `deploy/small/docker-compose.yml` | `deploy/small/docker-compose-mysql.yml` | `sql/tiers/small/small-init-mysql.sql` | `13306` |
| medium | `deploy/medium/docker-compose.yml` | `deploy/medium/docker-compose-mysql.yml` | `sql/tiers/medium/medium-init-mysql.sql` | `23306` |
| full | `deploy/full/docker-compose.yml` | `deploy/full/docker-compose-mysql.yml` | `sql/tiers/full/full-init-mysql.sql` | `3306` |

```bash
docker compose -f deploy/<tier>/docker-compose-mysql.yml up -d
```

两种数据库的数据卷互不复用（PostgreSQL 用 `postgres_data`，MySQL 用 `mysql_data`），
同一档位不要同时起两套编排。密码必须通过 `.env` 注入：MySQL 入口的
`MYSQL_PASSWORD`、`MYSQL_ROOT_PASSWORD` 与其余机密项都是 `${VAR:?...}` 形式，缺失即启动失败，
仓库内不提供任何明文默认值，示例值不得用于正式环境。

## 5. 合并与发布门禁

```powershell
. D:\code\Han\scripts\helpers\use-d-drive-dev-env.ps1
mvn -pl han-common/han-common-mybatis,han-modules/han-gen -am test -DskipTests=false
python scripts/checks/check_database_compat.py
python scripts/checks/check_sql_layout.py
python scripts/checks/check_deploy_layout.py
```

三档的 SQL 层均已在干净 MySQL 8.4.10 实例完成导入验证（证据见 §1.1），
但**服务层仍未验证**。进入具体项目部署前还必须完成：

1. 再次导入 `small-init-mysql.sql` 并记录目标 MySQL 小版本。
2. 启动 system、job，确认健康检查通过。
3. 完成登录、用户列表、部门/岗位/角色、字典、参数、公告和任务调度最小回归。
4. 验证分页 SQL 确实为 MySQL 方言。
5. 记录数据库版本、导入结果、服务版本和回滚点。

## 6. 回滚与边界

- 切回 PostgreSQL：停止 MySQL small 编排，使用 `deploy/small/docker-compose.yml` 和原 PostgreSQL 数据卷重新启动。
- 两种数据库的数据卷互不复用；切换数据库不会自动迁移业务数据。
- 当前不提供 PostgreSQL 与 MySQL 之间的数据转换脚本。
- 当前不提供历史 MySQL 库升级、medium/full MySQL 或 MySQL 增量升级承诺。
- 95 正式环境仍为 PostgreSQL，未经单独审批不得替换。
