-- =============================================
-- 20260813 文件管理权限串对齐到仓库约定（幂等）
--
-- 背景：
--   全仓约定是 `:list` 挂列表接口（GET /list）并兼作菜单可见性权限，
--   `:query` 挂按主键查详情的接口（GET /{id}）。二十多个模块都是这个口径。
--   han-file 是唯一的例外：它的 GET /file/list 用的是 file:query，
--   而菜单里播的页面权限是 file:list，两者对不上；file 模块又没有详情接口，
--   于是 file:query 成了一个"挂在列表接口上的详情权限"。
--   本轮把接口注解改成 file:list，file:query 随之失去对应接口，予以下线。
--
-- 影响：
--   已部署环境里，凡是只授了 file:query 而没授 file:list 的角色，
--   改注解后会直接丢掉文件列表权限。本脚本先把 file:list 补授给这些角色，
--   再删除 file:query 菜单及其角色绑定，保证升级前后可见范围不变。
--
-- 幂等策略：
--   1. 全部按权限串（perms）定位，不依赖固定菜单 ID。
--   2. 补授走 INSERT IGNORE，主键 (role_id, menu_id) 自动挡掉重复。
--   3. file:query 菜单不存在时，两个 ID 变量为 NULL，后续语句匹配不到任何行，
--      可重复执行。
--   4. 所有只读与临时表操作都排在基表写入之前，中途报错时基表保持原样；
--      即便部分执行，重跑一遍也能收敛到同一结果。
--
-- 两个 MySQL 专有限制（PostgreSQL 版不存在，写法差异全部由它们导致）：
--   1093：不允许在写某表的同时于子查询里读同一张表。因此待补授的角色先落到
--         会话临时表 han_file_perm_roles，再从临时表插回 sys_role_menu。
--   1137：同一条语句里不允许两次打开同一张临时表。所以两个菜单 ID 用会话变量
--         承载而不是临时表——变量可以在一条语句里任意次引用。早期版本曾把
--         ID 放在临时表 han_file_perm_ids 里并在同一条 SELECT 中以 p、q
--         两个别名引用，实测直接报 1137。
--
-- 与 PostgreSQL 版的差异：
--   RAISE NOTICE 用会话临时表 han_upgrade_notice 承接，脚本末尾整表输出。
--
-- 回滚：
--   本脚本删除了 file:query 菜单，回滚需要重新插入该菜单并恢复角色绑定；
--   由于 file:list 是超集授权，回滚时应同时评估是否收回新授的 file:list。
-- =============================================

SET NAMES utf8mb4;

-- ---------------------------------------------
-- 0. 执行报告表（PostgreSQL RAISE NOTICE 的替代）
-- ---------------------------------------------
DROP TEMPORARY TABLE IF EXISTS han_upgrade_notice;
CREATE TEMPORARY TABLE han_upgrade_notice (
    noted_at        TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    notice_level    VARCHAR(8)      NOT NULL,
    notice_scope    VARCHAR(64)     NOT NULL,
    notice_text     VARCHAR(1000)   NOT NULL
) DEFAULT CHARSET = utf8mb4;

-- ---------------------------------------------
-- 1. 定位两个菜单（用会话变量，见文件头 1137 说明）
-- ---------------------------------------------
SET @query_menu_id = (SELECT MIN(id) FROM sys_menu WHERE perms = 'file:query');
SET @list_menu_id  = (SELECT MIN(id) FROM sys_menu WHERE perms = 'file:list');

-- ---------------------------------------------
-- 2. 把「持有 file:query 但没有 file:list」的角色先取到临时表
--    直接 INSERT INTO sys_role_menu ... SELECT ... FROM sys_role_menu 会触发 1093。
-- ---------------------------------------------
DROP TEMPORARY TABLE IF EXISTS han_file_perm_roles;
CREATE TEMPORARY TABLE han_file_perm_roles (
    role_id BIGINT NOT NULL PRIMARY KEY
) DEFAULT CHARSET = utf8mb4;

INSERT IGNORE INTO han_file_perm_roles (role_id)
SELECT rm.role_id
FROM sys_role_menu rm
WHERE rm.menu_id = @query_menu_id
  AND @list_menu_id IS NOT NULL
  AND rm.role_id NOT IN (
      SELECT role_id FROM (
          SELECT x.role_id FROM sys_role_menu x WHERE x.menu_id = @list_menu_id
      ) AS already_granted
  );

-- ---------------------------------------------
-- 3. 补授 file:list（第一条基表写入，此前所有步骤都不改基表）
-- ---------------------------------------------
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, @list_menu_id
FROM han_file_perm_roles r
WHERE @list_menu_id IS NOT NULL;

-- HAVING 而不是 WHERE：这两条都是无 GROUP BY 的聚合查询，WHERE 滤光行也照样
-- 返回一行，只有 HAVING 能在「本次无事可做」时整条不输出。
INSERT INTO han_upgrade_notice (notice_level, notice_scope, notice_text)
SELECT 'INFO', 'file:list 补授',
       CONCAT('为 ', COUNT(*), ' 个原本只有 file:query 的角色补授了 file:list')
FROM han_file_perm_roles
HAVING @query_menu_id IS NOT NULL;

-- ---------------------------------------------
-- 4. 解绑并删除 file:query 菜单
-- ---------------------------------------------
INSERT INTO han_upgrade_notice (notice_level, notice_scope, notice_text)
SELECT 'INFO', 'file:query 下线',
       CONCAT('解除 ', COUNT(*), ' 条 file:query 角色绑定并删除该菜单')
FROM sys_role_menu
WHERE menu_id = @query_menu_id
HAVING @query_menu_id IS NOT NULL;

DELETE FROM sys_role_menu WHERE menu_id = @query_menu_id;

DELETE FROM sys_menu WHERE id = @query_menu_id;

INSERT INTO han_upgrade_notice (notice_level, notice_scope, notice_text)
SELECT 'INFO', '执行结果', '未发现 file:query 菜单，无需处理'
FROM DUAL
WHERE @query_menu_id IS NULL;

-- ---------------------------------------------
-- 5. 输出执行报告并清理临时对象
--    出现 WARN 表示有内容被跳过，需要人工确认。
-- ---------------------------------------------
SELECT notice_level AS 级别, notice_scope AS 范围, notice_text AS 说明
FROM han_upgrade_notice
ORDER BY noted_at;

DROP TEMPORARY TABLE IF EXISTS han_file_perm_roles;
DROP TEMPORARY TABLE IF EXISTS han_upgrade_notice;
