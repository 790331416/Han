# PostgreSQL/MySQL 兼容与切换手册

## 1. 结论与支持矩阵

PostgreSQL 保持 Han 的默认数据库。MySQL 8.4 是正式可选数据库，但只开放已经具备独立 SQL、Compose 和门禁的范围。

| 范围 | PostgreSQL | MySQL 8.4 | 说明 |
| --- | --- | --- | --- |
| 公共 MyBatis、分页、多租户 | 支持 | 支持 | 方言按 JDBC URL 自动识别 |
| 业务模块 JDBC 驱动 | 支持 | 支持 | 两种驱动随数据库模块一起构建 |
| 代码生成器元数据查询 | 支持 | 支持 | 按数据库产品名选择 mapper SQL |
| small clean 初始化 | 支持 | 支持 | 独立 SQL/Compose 与 MySQL 8.4.10 clean 导入已验证 |
| medium/full clean 初始化 | 支持 | 支持（未实库验证） | 已补齐独立 SQL 与 Compose，见下方 §1.1 |
| 存量升级 | 支持 | 支持（未实库验证） | `sql/upgrades/mysql/`，起算点见下方 §1.2 |

### 1.1 medium/full 的 MySQL 状态

`sql/tiers/{medium,full}/{medium,full}-init-mysql.sql` 与
`deploy/{medium,full}/docker-compose-mysql.yml` 已经落地，并做过与 PostgreSQL 版的逐项静态比对：
三档的菜单 ID 集合、权限点集合与 PostgreSQL 版完全一致，无重复 ID、无孤儿父节点。

**但这两档尚未做过真实 MySQL 实例导入**。small 档有 8.4.10 实库导入证据，medium/full 没有。
进入任何正式环境前必须先补实库导入与服务启动验证，不能拿 small 的验证结果替代。

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

**验证状态要分档看**：small MySQL 的 SQL 层已在干净 MySQL 8.4.10 实例验证过；
medium/full 只做过与 PostgreSQL 版的静态逐项比对，**没有实库导入证据**，
两档的实库导入与服务启动验证仍是欠账，不能拿 small 的结论替代。

进入具体项目部署前还必须完成：

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
