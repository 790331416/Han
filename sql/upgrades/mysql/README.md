# MySQL 存量升级脚本

本目录只承载 **MySQL 8.4** 的存量库升级脚本。PostgreSQL 仍是 Han 的默认数据库，
它的升级脚本在 `sql/upgrades/postgres/`，两个目录互不替代、互不引用。

## 1. 起算点：2026-08-11

MySQL 8.4 是 2026-08-11 才作为可选数据库引入的（`sql/tiers/small/small-init-mysql.sql` 首版）。
在那之前不存在任何 MySQL 库，因此本通道只承载 **2026-08-11 之后**产生的增量变更。

## 2. 为什么不回港 2026-08-11 之前的历史脚本

`sql/upgrades/postgres/` 目前有 37 个脚本，其中 35 个早于 MySQL 引入。这 35 个**一律不回港**：

- 它们描述的是 PostgreSQL 库从更早形态演进到今天的过程，而 MySQL 库最早也只能是
  2026-08-11 建的，那时的初始化脚本已经把这些变更的**最终结果**直接烘焙进了建表语句。
- 回港这些脚本，在任何一个真实存在的 MySQL 库上都不会命中任何变更，属于死代码；
  但每一份都要额外承担方言翻译错误、误删索引、误改数据的风险。
- 判断依据是「MySQL 库的最早可能形态」，不是「PostgreSQL 脚本的日期是否久远」。
  以后若发现某个历史 PostgreSQL 变更**没有**被 `*-init-mysql.sql` 覆盖，
  正确做法是补一份新的 MySQL 升级脚本（按当天日期命名），而不是把旧脚本翻译过来。

已知的一处例外处理方式，见本文件第 6 节。

## 3. 全新安装不需要执行这些脚本

新建库请直接按档位导入初始化脚本，导入完即为最新结构，**不要**再执行本目录下的任何脚本：

| 档位 | MySQL 初始化脚本 |
| --- | --- |
| small | `sql/tiers/small/small-init-mysql.sql` |
| medium | `sql/tiers/medium/medium-init-mysql.sql` |
| full | `sql/tiers/full/full-init-mysql.sql` |

本目录的脚本虽然都是幂等的，在新库上执行不会报错，但也只会把「已经是目标状态」原样跳过，
徒增一次不必要的 DDL 风险窗口。

## 4. 命名与执行顺序

- 文件名固定为 `yyyyMMdd_变更主题.sql`，日期用变更**产生**的日期，不用执行日期。
- 同一天多个脚本时，主题名按字典序即为执行顺序；有强依赖关系的变更不要拆到同一天的两个文件里。
- 执行顺序就是**文件名升序**。跨版本升级时按顺序把区间内的脚本全部执行一遍。
- 已发布的脚本不允许原地改语义。发现问题追加新脚本修正，保持升级链可重放。

当前脚本清单：

| 顺序 | 脚本 | 作用 |
| --- | --- | --- |
| 1 | `20260812_permission_seed_alignment.sql` | 补齐权限点菜单种子，统一权限串，给超管补授权 |
| 2 | `20260812_unique_constraint_del_flag_alignment.sql` | 唯一约束与逻辑删除对齐，`sys_user_social` 结构兜底 |

两个脚本之间没有强依赖，但建议按上表顺序执行：先补数据、再改约束，
这样约束脚本的重复数据检查能看到最终数据。

## 5. 与 `sql/upgrades/postgres/` 的对应关系

| 本目录脚本 | 对应 PostgreSQL 脚本 | 关系 |
| --- | --- | --- |
| `20260812_permission_seed_alignment.sql` | `sql/upgrades/postgres/20260812_permission_seed_alignment.sql` | 同名、同语义，逐段对应 |
| `20260812_unique_constraint_del_flag_alignment.sql` | `sql/upgrades/postgres/20260812_unique_constraint_del_flag_alignment.sql` | 同名、同语义，另多处理一张 `sys_user`（见第 6 节） |
| 无 | 其余 35 个 PostgreSQL 脚本 | 早于 MySQL 引入，按第 2 节的理由不回港 |

同一变更在两个数据库上必须**同名**，方便对照与漏项排查。两侧脚本要成对维护：
改了 PostgreSQL 版的语义，MySQL 版必须同步，反之亦然。

## 6. 唯一的一处范围差异：`sys_user`

`20260812_unique_constraint_del_flag_alignment.sql` 比 PostgreSQL 版多处理一张 `sys_user`。

PostgreSQL 侧 `sys_user` 的唯一约束由 `sql/upgrades/postgres/phase5_unique_constraint.sql` 处理，
那个脚本早于 MySQL 引入、按第 2 节不回港；而 2026-08-11 首版 MySQL 初始化脚本恰好把
**旧写法**（建表内联 `UNIQUE (username, tenant_id)`，不含 `del_flag`）烘焙进了存量库。
也就是说，这一条不属于「变更已烘焙在初始化脚本里」，而是「错误状态被烘焙进去了」，
所以必须在 MySQL 通道里补上。这也是第 2 节最后一句的具体含义。

## 7. 执行方式与前置条件

两个脚本都用到了存储过程，**必须用支持 `DELIMITER` 的客户端执行**
（`mysql` 命令行、MySQL Workbench、DBeaver 均可；不要用只会按分号切语句的简易工具）：

```bash
mysql --default-character-set=utf8mb4 -h <host> -P <port> -u <user> -p <database> \
  < sql/upgrades/mysql/20260812_permission_seed_alignment.sql
```

执行账号需要的权限：`SELECT`、`INSERT`、`UPDATE`、`ALTER`、`INDEX`、`CREATE`、
`CREATE ROUTINE`、`ALTER ROUTINE`、`EXECUTE`、`CREATE TEMPORARY TABLES`。

执行前必须先做一次可恢复的备份（`mysqldump` 或快照）。MySQL 的 DDL 自动提交、
不能事务回滚，脚本头部的「回滚」段给的是反向语句，不是自动回滚能力。

## 8. 幂等性与执行报告

本目录所有脚本都必须可重复执行且不报错。统一采用同一套写法，新脚本请沿用：

1. 存在性判断一律查 `information_schema`（MySQL 8.4 不支持 `CREATE INDEX IF NOT EXISTS`、
   `DROP INDEX IF EXISTS`、`ADD COLUMN IF NOT EXISTS`）。
2. 需要循环和条件分支的逻辑放进临时存储过程，`CALL` 之后立刻 `DROP PROCEDURE`；
   种子数据放会话临时表，脚本末尾 `DROP TEMPORARY TABLE`。执行完库里不留常驻对象。
3. 需要拼表名、索引名的动态 DDL 用 `PREPARE` + `EXECUTE` + `DEALLOCATE PREPARE`。
4. 不用游标：游标的 `NOT FOUND` 处理器会被循环体里任何「查不到行的 `SELECT ... INTO`」
   提前触发，把循环静默截断。改用按连续 `seq` 遍历，并让所有取值 `SELECT` 都走聚合函数，
   保证永远返回且只返回一行。
5. MySQL 不允许在 `INSERT ... SELECT` / `UPDATE` 的子查询里读被写的那张表（错误 1093），
   PostgreSQL 版常用的 `WHERE NOT EXISTS (SELECT ... FROM 目标表)` 必须拆成
   「先 `SELECT COUNT(*) INTO` 变量，再按变量决定是否写」。

PostgreSQL 版用 `RAISE NOTICE` 打印跳过原因，MySQL 没有对应物。本目录的替代方案是：
所有新增、修改、跳过都写进会话临时表 `han_upgrade_notice`，脚本**最后一条语句**
把它整表 `SELECT` 出来作为执行报告。看结果只需要两条规则：

- 出现 `WARN` 行 = 有内容被跳过且需要人工处理（重复数据、权限串冲突、结构不符），
  处理完要重跑对应脚本。
- 只有 `INFO` 行 = 本次执行已达成目标状态。

## 9. 已知边界

- **本通道的脚本没有在真实 MySQL 实例上跑过**，只做了静态编写与逐段语义比对。
  进入任何正式环境前必须先在备份还原出来的库上演练，并记录执行报告。
- 本通道**不覆盖** 2026-08-11 首版 `small-init-mysql.sql` 与当前版本之间的其余结构差异，
  例如 `sys_post` 的排序列在首版叫 `sort`、当前叫 `post_sort`，以及首版缺失的任务调度
  菜单种子。用首版建过库的环境需要单独评估这些差异，不要假设执行完本目录脚本就已对齐。
- 升级出来的库与全新安装的库，**菜单 ID 可能不同**。升级脚本优先用与 PostgreSQL 版共用的
  那套预留编号，编号被占用时退化为 `MAX(id) + 1`；初始化脚本用的是另一套连续编号。
  菜单 ID 不被代码引用（父子关系由 `parent_id` / `ancestors` 表达），但做两库结构比对时
  不要拿菜单 ID 当对齐依据。这一行为与 PostgreSQL 版一致。
- 三档 MySQL 初始化脚本现已统一使用 `del_flag` 函数式索引键
  `(IF(del_flag = 0, 0, NULL))`，与 PostgreSQL 的 `WHERE del_flag = 0` 部分索引等价。
  medium/full 早期版本曾漏掉这个键部件（软删后无法重建同名对象），已随初始化脚本一并修正。
  `20260812_unique_constraint_del_flag_alignment.sql` 遇到不含该键部件的同名索引时
  只报 `WARN`、不自动重建，避免把升级出来的库改成和 init 脚本对不上的形态；
  正常情况下用当前 init 建的库不会触发这条 WARN。
- 95 正式环境仍为 PostgreSQL，本目录脚本不在 95 的发布路径内。
