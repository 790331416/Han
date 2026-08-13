-- =============================================
-- 20260812 权限点种子补齐与权限标识对齐（幂等 · MySQL 8.4）
--
-- 对应 PostgreSQL 版：sql/upgrades/postgres/20260812_permission_seed_alignment.sql
--
-- 背景：
--   控制器 @PreAuthorize 声明的权限串与 sys_menu.perms 大面积对不上。
--   PermissionService.hasAuthority 只对 userId = 1 短路放行，因此 sys_menu 里没有
--   对应记录的权限点无法挂到 sys_role_menu，非超管调用一律 403。
--   本脚本把 AI / job / tenant / OSS / 开放平台 / 日志与在线用户的权限点补齐，
--   并把历史遗留的第二套权限串统一到后端注解口径。
--
--   MySQL 侧还有一层：2026-08-11 首版 sql/tiers/small/small-init-mysql.sql 缺了
--   任务调度目录与 1100 段按钮权限，用那一版建的库同样缺这批权限点。
--   本脚本按语义键补齐，与三档 *-init-mysql.sql 的最终形态收敛到同一结果。
--
-- 幂等策略（全文只用这一种风格）：
--   1. MySQL 8.4 没有 PostgreSQL 的匿名代码块，逐条判断只能落在存储过程里。
--      统一写法是「临时表存种子 + 存储过程 WHILE 遍历 + information_schema 判存在性」，
--      需要拼表名时才用 PREPARE/EXECUTE。存储过程与临时表在脚本末尾全部删除，
--      库里不留常驻对象。
--   2. 不用游标：游标的 NOT FOUND 处理器会被循环体里任何「查不到行的 SELECT ... INTO」
--      提前触发，把循环静默截断。这里改成按连续 seq 顺序取行，且所有取值 SELECT 一律走
--      聚合函数（COUNT / MIN / MAX），保证永远返回且只返回一行。
--   3. 全部按语义键（perms / menu_type + path）判断存在性，不依赖固定主键 ID。
--   4. 菜单 ID 优先使用 sql/tiers/*/*-init*.sql 与 phase9_base_menu_backfill.sql
--      共用的那套编号，被占用时退化为 MAX(id) + 1。
--   5. 按模块表是否存在做档位裁剪：small 没有 ai_model / open_app / wf_category /
--      sys_oss_config / sys_tenant，对应菜单不会被插入。
--   6. MySQL 不允许在 INSERT ... SELECT / UPDATE 的子查询里读被写的那张表
--      （ER_UPDATE_TABLE_USED，1093），PostgreSQL 版里 `NOT EXISTS (SELECT ... FROM sys_menu)`
--      这种写法一律改成「先 SELECT COUNT(*) INTO 变量，再按变量决定是否写」。
--   7. PostgreSQL 的 RAISE NOTICE 在 MySQL 没有对应物：所有新增、重命名、跳过都写进
--      会话临时表 han_upgrade_notice，脚本最后一条语句把它整表 SELECT 出来。
--      预期内的档位裁剪按计数汇总，需要人工处理的情况单独记 WARN，不会静默。
--
-- 执行方式：
--   本文件包含存储过程，必须用支持 DELIMITER 的客户端执行（mysql 命令行、
--   MySQL Workbench、DBeaver 均可）：
--     mysql --default-character-set=utf8mb4 -h <host> -P <port> -u <user> -p <db> < 本文件
--   需要的权限：SELECT / INSERT / UPDATE / CREATE ROUTINE / ALTER ROUTINE / EXECUTE /
--   CREATE TEMPORARY TABLES。
--
-- 回滚：
--   DELETE FROM sys_role_menu WHERE menu_id IN (SELECT id FROM sys_menu WHERE perms IN (...));
--   DELETE FROM sys_menu WHERE perms IN (...);
--   两条语句必须按上面的顺序执行（先删关联再删菜单）。
--   目录菜单没有 perms，按 menu_type = 'M' AND path IN ('job', 'workflow', 'open', 'ai') 反查。
--   （权限串重命名部分需按第 1 段的对照表反向 UPDATE）
-- =============================================

SET NAMES utf8mb4;

-- ---------------------------------------------
-- 0. 执行报告表（PostgreSQL RAISE NOTICE 的替代）
--    会话级临时表，脚本末尾整表输出后删除，不落任何常驻对象。
-- ---------------------------------------------
DROP TEMPORARY TABLE IF EXISTS han_upgrade_notice;
CREATE TEMPORARY TABLE han_upgrade_notice (
    noted_at        TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    notice_level    VARCHAR(8)      NOT NULL,
    notice_scope    VARCHAR(64)     NOT NULL,
    notice_text     VARCHAR(1000)   NOT NULL
) DEFAULT CHARSET = utf8mb4;

-- ---------------------------------------------
-- 1. 权限标识统一到后端 @PreAuthorize 口径
--    历史上同一功能在 tier init 与 phase9 里各有一套写法，这里把 phase9 那套改名过来。
--    目标串已存在时不动（避免制造重复 perms），留给人工确认后清理。
-- ---------------------------------------------
DROP TEMPORARY TABLE IF EXISTS han_perms_rename;
CREATE TEMPORARY TABLE han_perms_rename (
    seq         INT             NOT NULL PRIMARY KEY,
    old_perms   VARCHAR(200)    NOT NULL,
    new_perms   VARCHAR(200)    NOT NULL
) DEFAULT CHARSET = utf8mb4;

INSERT INTO han_perms_rename (seq, old_perms, new_perms) VALUES
(1, 'system:operlog:list',   'monitor:operlog:list'),
(2, 'system:loginlog:list',  'monitor:loginlog:list'),
(3, 'system:monitor:server', 'monitor:server:list'),
(4, 'system:monitor:cache',  'monitor:cache:list');

DROP PROCEDURE IF EXISTS han_up_20260812_rename_perms;
DELIMITER $$
CREATE PROCEDURE han_up_20260812_rename_perms()
BEGIN
    DECLARE v_seq       INT DEFAULT 1;
    DECLARE v_total     INT DEFAULT 0;
    DECLARE v_old       VARCHAR(200);
    DECLARE v_new       VARCHAR(200);
    DECLARE v_old_cnt   INT;
    DECLARE v_new_cnt   INT;
    DECLARE v_rows      INT;
    DECLARE v_renamed   INT DEFAULT 0;
    DECLARE v_skipped   INT DEFAULT 0;

    SELECT COUNT(*) INTO v_total FROM han_perms_rename;

    rename_loop: WHILE v_seq <= v_total DO
        SELECT old_perms, new_perms INTO v_old, v_new
        FROM han_perms_rename
        WHERE seq = v_seq;

        -- 先自增，后面任何一处 ITERATE 都不会把循环卡死
        SET v_seq = v_seq + 1;

        SELECT COUNT(*) INTO v_old_cnt FROM sys_menu WHERE perms = v_old;
        SELECT COUNT(*) INTO v_new_cnt FROM sys_menu WHERE perms = v_new;

        -- 没有历史串：本来就是目标口径，无需处理
        IF v_old_cnt = 0 THEN
            SET v_skipped = v_skipped + 1;
            ITERATE rename_loop;
        END IF;

        -- 新旧串同时存在：改名会造出重复 perms，交人工清理
        IF v_new_cnt > 0 THEN
            INSERT INTO han_upgrade_notice (notice_level, notice_scope, notice_text)
            VALUES ('WARN', '权限串重命名',
                    CONCAT('跳过：', v_old, ' 与 ', v_new, ' 同时存在，未改名，请人工确认后清理旧串'));
            SET v_skipped = v_skipped + 1;
            ITERATE rename_loop;
        END IF;

        UPDATE sys_menu SET perms = v_new WHERE perms = v_old;
        SET v_rows = ROW_COUNT();
        SET v_renamed = v_renamed + 1;

        INSERT INTO han_upgrade_notice (notice_level, notice_scope, notice_text)
        VALUES ('INFO', '权限串重命名', CONCAT('已把 ', v_old, ' 改为 ', v_new, '，影响 ', v_rows, ' 行'));
    END WHILE rename_loop;

    -- AI 对话菜单历史上 perms 为空，导致 AiChatController 声明的 ai:chat:list 无法授权。
    -- PostgreSQL 版把「目标串不存在」写成 UPDATE 里的 NOT EXISTS 子查询，MySQL 不允许
    -- UPDATE 的子查询读同一张表（1093），拆成先查计数再决定是否更新。
    SELECT COUNT(*) INTO v_new_cnt FROM sys_menu WHERE perms = 'ai:chat:list';

    IF v_new_cnt = 0 THEN
        UPDATE sys_menu
        SET perms = 'ai:chat:list'
        WHERE perms IS NULL
          AND menu_type = 'C'
          AND path = 'chat'
          AND component = 'ai/chat/index';

        SET v_rows = ROW_COUNT();
        IF v_rows > 0 THEN
            INSERT INTO han_upgrade_notice (notice_level, notice_scope, notice_text)
            VALUES ('INFO', '权限串重命名', CONCAT('已给 AI 对话菜单补上 ai:chat:list，影响 ', v_rows, ' 行'));
        END IF;
    END IF;

    INSERT INTO han_upgrade_notice (notice_level, notice_scope, notice_text)
    VALUES ('INFO', '汇总', CONCAT('权限串重命名：改名 ', v_renamed, ' 组，跳过 ', v_skipped, ' 组'));
END $$
DELIMITER ;

CALL han_up_20260812_rename_perms();
DROP PROCEDURE han_up_20260812_rename_perms;

-- ---------------------------------------------
-- 2. 目录菜单（M）：任务调度 / 工作流 / 开放平台 / AI 智能
--    guard_table 是档位裁剪依据：对应模块表不存在就不插菜单。
-- ---------------------------------------------
DROP TEMPORARY TABLE IF EXISTS han_menu_dir;
CREATE TEMPORARY TABLE han_menu_dir (
    seq             INT             NOT NULL PRIMARY KEY,
    dir_path        VARCHAR(200)    NOT NULL,
    menu_name       VARCHAR(100)    NOT NULL,
    icon            VARCHAR(100)    NOT NULL,
    sort_no         INT             NOT NULL,
    preferred_id    BIGINT          NOT NULL,
    guard_table     VARCHAR(64)     NOT NULL
) DEFAULT CHARSET = utf8mb4;

INSERT INTO han_menu_dir (seq, dir_path, menu_name, icon, sort_no, preferred_id, guard_table) VALUES
(1, 'job',      '任务调度', 'timer',       5, 200, 'sys_job'),
(2, 'workflow', '工作流',   'connection',  6, 300, 'wf_category'),
(3, 'open',     '开放平台', 'platform',    7, 400, 'open_app'),
(4, 'ai',       'AI智能',   'magic-stick', 8, 500, 'ai_model');

DROP PROCEDURE IF EXISTS han_up_20260812_seed_dir;
DELIMITER $$
CREATE PROCEDURE han_up_20260812_seed_dir()
BEGIN
    DECLARE v_seq           INT DEFAULT 1;
    DECLARE v_total         INT DEFAULT 0;
    DECLARE v_path          VARCHAR(200);
    DECLARE v_name          VARCHAR(100);
    DECLARE v_icon          VARCHAR(100);
    DECLARE v_sort          INT;
    DECLARE v_preferred_id  BIGINT;
    DECLARE v_guard         VARCHAR(64);
    DECLARE v_cnt           INT;
    DECLARE v_next_id       BIGINT;
    DECLARE v_added         INT DEFAULT 0;
    DECLARE v_exists        INT DEFAULT 0;
    DECLARE v_no_module     INT DEFAULT 0;

    SELECT COUNT(*) INTO v_total FROM han_menu_dir;

    dir_loop: WHILE v_seq <= v_total DO
        SELECT dir_path, menu_name, icon, sort_no, preferred_id, guard_table
        INTO v_path, v_name, v_icon, v_sort, v_preferred_id, v_guard
        FROM han_menu_dir
        WHERE seq = v_seq;

        SET v_seq = v_seq + 1;

        SELECT COUNT(*) INTO v_cnt
        FROM information_schema.tables
        WHERE table_schema = DATABASE() AND table_name = v_guard;

        IF v_cnt = 0 THEN
            SET v_no_module = v_no_module + 1;
            ITERATE dir_loop;
        END IF;

        SELECT COUNT(*) INTO v_cnt
        FROM sys_menu
        WHERE menu_type = 'M' AND path = v_path;

        IF v_cnt > 0 THEN
            SET v_exists = v_exists + 1;
            ITERATE dir_loop;
        END IF;

        SELECT COUNT(*) INTO v_cnt FROM sys_menu WHERE id = v_preferred_id;

        IF v_cnt > 0 THEN
            SELECT COALESCE(MAX(id), 0) + 1 INTO v_next_id FROM sys_menu;
        ELSE
            SET v_next_id = v_preferred_id;
        END IF;

        INSERT INTO sys_menu (
            id, tenant_id, parent_id, ancestors, menu_name, menu_type,
            path, component, perms, icon, sort, visible, status
        )
        VALUES (
            v_next_id, NULL, 0, '0', v_name, 'M',
            v_path, NULL, NULL, v_icon, v_sort, 0, 0
        );

        SET v_added = v_added + 1;
        INSERT INTO han_upgrade_notice (notice_level, notice_scope, notice_text)
        VALUES ('INFO', '目录菜单', CONCAT('已新增目录 ', v_name, '（path=', v_path, ', id=', v_next_id, '）'));
    END WHILE dir_loop;

    INSERT INTO han_upgrade_notice (notice_level, notice_scope, notice_text)
    VALUES ('INFO', '汇总',
            CONCAT('目录菜单：新增 ', v_added, ' 个，已存在 ', v_exists, ' 个，因本档位没有对应模块表跳过 ', v_no_module, ' 个'));
END $$
DELIMITER ;

CALL han_up_20260812_seed_dir();
DROP PROCEDURE han_up_20260812_seed_dir;

-- ---------------------------------------------
-- 3. 页面菜单（C）：父目录按 menu_type + path 解析
-- ---------------------------------------------
DROP TEMPORARY TABLE IF EXISTS han_menu_page;
CREATE TEMPORARY TABLE han_menu_page (
    seq             INT             NOT NULL PRIMARY KEY,
    perms           VARCHAR(200)    NOT NULL,
    menu_name       VARCHAR(100)    NOT NULL,
    parent_path     VARCHAR(200)    NOT NULL,
    menu_path       VARCHAR(200)    NOT NULL,
    component       VARCHAR(255)    NOT NULL,
    icon            VARCHAR(100)    NOT NULL,
    sort_no         INT             NOT NULL,
    preferred_id    BIGINT          NOT NULL,
    guard_table     VARCHAR(64)     NOT NULL
) DEFAULT CHARSET = utf8mb4;

INSERT INTO han_menu_page
    (seq, perms, menu_name, parent_path, menu_path, component, icon, sort_no, preferred_id, guard_table) VALUES
( 1, 'job:list',                 '定时任务',    'job',      'list',       'job/index',                  'clock',            1, 210, 'sys_job'),
( 2, 'job:log:list',             '调度日志',    'job',      'log',        'job/log',                    'document',         2, 211, 'sys_job_log'),
( 3, 'workflow:definition:list', '流程定义',    'workflow', 'definition', 'workflow/definition/index',  'document',         1, 310, 'wf_category'),
( 4, 'workflow:instance:list',   '流程实例',    'workflow', 'instance',   'workflow/instance/index',    'histogram',        2, 311, 'wf_instance_extend'),
( 5, 'workflow:task:todo',       '待办任务',    'workflow', 'todo',       'workflow/task/index',        'bell',             3, 312, 'wf_category'),
( 6, 'workflow:task:done',       '已办任务',    'workflow', 'done',       'workflow/task/done',         'finished',         4, 313, 'wf_category'),
( 7, 'open:app:list',            '应用管理',    'open',     'app',        'open/app/index',             'grid',             1, 410, 'open_app'),
( 8, 'ai:model:list',            'AI模型管理',  'ai',       'model',      'ai/model/index',             'cpu',              1, 510, 'ai_model'),
( 9, 'ai:kb:list',               '知识库',      'ai',       'knowledge',  'ai/knowledge/index',         'collection',       2, 511, 'ai_knowledge_base'),
(10, 'ai:mcp:list',              'MCP管理',     'ai',       'mcp',        'ai/mcp/index',               'link',             3, 512, 'ai_mcp_server'),
(11, 'ai:agent:list',            '智能体',      'ai',       'agent',      'ai/agent/index',             'user-filled',      4, 513, 'ai_agent'),
(12, 'ai:workflow:list',         'AI工作流',    'ai',       'workflow',   'ai/workflow/index',          'chat-dot-round',   5, 514, 'ai_workflow'),
(13, 'ai:token:stats',           'Token统计',   'ai',       'token',      'ai/token/index',             'data-analysis',    7, 516, 'ai_token_usage'),
(14, 'ai:chat:list',             'AI对话',      'ai',       'chat',       'ai/chat/index',              'chat-line-square', 8, 517, 'ai_conversation');

DROP PROCEDURE IF EXISTS han_up_20260812_seed_page;
DELIMITER $$
CREATE PROCEDURE han_up_20260812_seed_page()
BEGIN
    DECLARE v_seq           INT DEFAULT 1;
    DECLARE v_total         INT DEFAULT 0;
    DECLARE v_perms         VARCHAR(200);
    DECLARE v_name          VARCHAR(100);
    DECLARE v_parent_path   VARCHAR(200);
    DECLARE v_path          VARCHAR(200);
    DECLARE v_component     VARCHAR(255);
    DECLARE v_icon          VARCHAR(100);
    DECLARE v_sort          INT;
    DECLARE v_preferred_id  BIGINT;
    DECLARE v_guard         VARCHAR(64);
    DECLARE v_cnt           INT;
    DECLARE v_parent_id     BIGINT;
    DECLARE v_next_id       BIGINT;
    DECLARE v_added         INT DEFAULT 0;
    DECLARE v_exists        INT DEFAULT 0;
    DECLARE v_no_module     INT DEFAULT 0;
    DECLARE v_no_parent     INT DEFAULT 0;

    SELECT COUNT(*) INTO v_total FROM han_menu_page;

    page_loop: WHILE v_seq <= v_total DO
        SELECT perms, menu_name, parent_path, menu_path, component, icon, sort_no, preferred_id, guard_table
        INTO v_perms, v_name, v_parent_path, v_path, v_component, v_icon, v_sort, v_preferred_id, v_guard
        FROM han_menu_page
        WHERE seq = v_seq;

        SET v_seq = v_seq + 1;

        SELECT COUNT(*) INTO v_cnt
        FROM information_schema.tables
        WHERE table_schema = DATABASE() AND table_name = v_guard;

        IF v_cnt = 0 THEN
            SET v_no_module = v_no_module + 1;
            ITERATE page_loop;
        END IF;

        SELECT COUNT(*) INTO v_cnt FROM sys_menu WHERE perms = v_perms;

        IF v_cnt > 0 THEN
            SET v_exists = v_exists + 1;
            ITERATE page_loop;
        END IF;

        -- MIN(id) 等价于 PostgreSQL 版的 ORDER BY id LIMIT 1，且聚合查询永远返回一行，
        -- 不会把「查不到父目录」变成异常路径
        SELECT MIN(id) INTO v_parent_id
        FROM sys_menu
        WHERE menu_type = 'M' AND path = v_parent_path;

        IF v_parent_id IS NULL THEN
            SET v_no_parent = v_no_parent + 1;
            ITERATE page_loop;
        END IF;

        SELECT COUNT(*) INTO v_cnt FROM sys_menu WHERE id = v_preferred_id;

        IF v_cnt > 0 THEN
            SELECT COALESCE(MAX(id), 0) + 1 INTO v_next_id FROM sys_menu;
        ELSE
            SET v_next_id = v_preferred_id;
        END IF;

        INSERT INTO sys_menu (
            id, tenant_id, parent_id, ancestors, menu_name, menu_type,
            path, component, perms, icon, sort, visible, status
        )
        VALUES (
            v_next_id, NULL, v_parent_id, CONCAT('0,', v_parent_id), v_name, 'C',
            v_path, v_component, v_perms, v_icon, v_sort, 0, 0
        );

        SET v_added = v_added + 1;
        INSERT INTO han_upgrade_notice (notice_level, notice_scope, notice_text)
        VALUES ('INFO', '页面菜单', CONCAT('已新增页面 ', v_name, '（perms=', v_perms, ', id=', v_next_id, '）'));
    END WHILE page_loop;

    INSERT INTO han_upgrade_notice (notice_level, notice_scope, notice_text)
    VALUES ('INFO', '汇总',
            CONCAT('页面菜单：新增 ', v_added, ' 个，已存在 ', v_exists,
                   ' 个，因本档位没有对应模块表跳过 ', v_no_module,
                   ' 个，因父目录不存在跳过 ', v_no_parent, ' 个'));
END $$
DELIMITER ;

CALL han_up_20260812_seed_page();
DROP PROCEDURE han_up_20260812_seed_page;

-- OSS 配置菜单挂在「系统管理」下，父目录解析方式与上面不同，单独处理
DROP PROCEDURE IF EXISTS han_up_20260812_seed_oss;
DELIMITER $$
CREATE PROCEDURE han_up_20260812_seed_oss()
BEGIN
    DECLARE v_cnt       INT;
    DECLARE v_parent_id BIGINT;
    DECLARE v_next_id   BIGINT;

    SELECT COUNT(*) INTO v_cnt
    FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'sys_oss_config';

    IF v_cnt = 0 THEN
        INSERT INTO han_upgrade_notice (notice_level, notice_scope, notice_text)
        VALUES ('INFO', 'OSS 配置菜单', '跳过：本档位没有 sys_oss_config');
    ELSE
        SELECT COUNT(*) INTO v_cnt FROM sys_menu WHERE perms = 'system:oss:list';

        IF v_cnt > 0 THEN
            INSERT INTO han_upgrade_notice (notice_level, notice_scope, notice_text)
            VALUES ('INFO', 'OSS 配置菜单', '跳过：system:oss:list 已存在');
        ELSE
            SELECT MIN(id) INTO v_parent_id
            FROM sys_menu
            WHERE menu_type = 'M' AND path = 'system';

            IF v_parent_id IS NULL THEN
                INSERT INTO han_upgrade_notice (notice_level, notice_scope, notice_text)
                VALUES ('WARN', 'OSS 配置菜单', '跳过：找不到「系统管理」目录（menu_type = M AND path = system），菜单树可能被改过');
            ELSE
                SELECT COALESCE(MAX(id), 0) + 1 INTO v_next_id FROM sys_menu;

                INSERT INTO sys_menu (
                    id, tenant_id, parent_id, ancestors, menu_name, menu_type,
                    path, component, perms, icon, sort, visible, status
                )
                VALUES (
                    v_next_id, NULL, v_parent_id, CONCAT('0,', v_parent_id), 'OSS配置', 'C',
                    'oss-config', 'system/oss-config/index', 'system:oss:list', 'upload', 11, 0, 0
                );

                INSERT INTO han_upgrade_notice (notice_level, notice_scope, notice_text)
                VALUES ('INFO', 'OSS 配置菜单', CONCAT('已新增 system:oss:list（id=', v_next_id, '）'));
            END IF;
        END IF;
    END IF;
END $$
DELIMITER ;

CALL han_up_20260812_seed_oss();
DROP PROCEDURE han_up_20260812_seed_oss;

-- 资源配额菜单挂在「租户管理」目录下（没有该目录时退回系统管理目录）
DROP PROCEDURE IF EXISTS han_up_20260812_seed_quota;
DELIMITER $$
CREATE PROCEDURE han_up_20260812_seed_quota()
BEGIN
    DECLARE v_cnt       INT;
    DECLARE v_parent_id BIGINT;
    DECLARE v_next_id   BIGINT;

    SELECT COUNT(*) INTO v_cnt
    FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'sys_tenant_quota';

    IF v_cnt = 0 THEN
        INSERT INTO han_upgrade_notice (notice_level, notice_scope, notice_text)
        VALUES ('INFO', '资源配额菜单', '跳过：本档位没有 sys_tenant_quota');
    ELSE
        SELECT COUNT(*) INTO v_cnt FROM sys_menu WHERE perms = 'tenant:quota:query';

        IF v_cnt > 0 THEN
            INSERT INTO han_upgrade_notice (notice_level, notice_scope, notice_text)
            VALUES ('INFO', '资源配额菜单', '跳过：tenant:quota:query 已存在');
        ELSE
            -- PostgreSQL 版用 ORDER BY CASE WHEN path = 'tenant' THEN 0 ELSE 1 END 表达优先级，
            -- 这里拆成两次聚合查询，语义相同且不依赖排序表达式
            SELECT MIN(id) INTO v_parent_id
            FROM sys_menu
            WHERE menu_type = 'M' AND path = 'tenant';

            IF v_parent_id IS NULL THEN
                SELECT MIN(id) INTO v_parent_id
                FROM sys_menu
                WHERE menu_type = 'M' AND path = 'system';
            END IF;

            IF v_parent_id IS NULL THEN
                INSERT INTO han_upgrade_notice (notice_level, notice_scope, notice_text)
                VALUES ('WARN', '资源配额菜单', '跳过：租户管理与系统管理目录都不存在，菜单树可能被改过');
            ELSE
                SELECT COALESCE(MAX(id), 0) + 1 INTO v_next_id FROM sys_menu;

                INSERT INTO sys_menu (
                    id, tenant_id, parent_id, ancestors, menu_name, menu_type,
                    path, component, perms, icon, sort, visible, status
                )
                VALUES (
                    v_next_id, NULL, v_parent_id, CONCAT('0,', v_parent_id), '资源配额', 'C',
                    'quota', 'tenant/quota/index', 'tenant:quota:query', 'pie-chart', 3, 0, 0
                );

                INSERT INTO han_upgrade_notice (notice_level, notice_scope, notice_text)
                VALUES ('INFO', '资源配额菜单', CONCAT('已新增 tenant:quota:query（id=', v_next_id, '）'));
            END IF;
        END IF;
    END IF;
END $$
DELIMITER ;

CALL han_up_20260812_seed_quota();
DROP PROCEDURE han_up_20260812_seed_quota;

-- ---------------------------------------------
-- 4. 按钮权限（F）：父菜单按 perms 解析，父菜单不存在说明该档位没部署对应模块，跳过
-- ---------------------------------------------
DROP TEMPORARY TABLE IF EXISTS han_menu_button;
CREATE TEMPORARY TABLE han_menu_button (
    seq             INT             NOT NULL PRIMARY KEY,
    perms           VARCHAR(200)    NOT NULL,
    menu_name       VARCHAR(100)    NOT NULL,
    parent_perms    VARCHAR(200)    NOT NULL,
    sort_no         INT             NOT NULL
) DEFAULT CHARSET = utf8mb4;

INSERT INTO han_menu_button (seq, perms, menu_name, parent_perms, sort_no) VALUES
-- 用户 / 日志 / 在线用户
( 1, 'system:user:unbind',         '社交解绑',         'system:user:list',     8),
( 2, 'monitor:operlog:export',     '操作日志导出',     'monitor:operlog:list', 1),
( 3, 'monitor:operlog:remove',     '操作日志删除',     'monitor:operlog:list', 2),
( 4, 'monitor:loginlog:export',    '登录日志导出',     'monitor:loginlog:list', 1),
( 5, 'monitor:loginlog:remove',    '登录日志删除',     'monitor:loginlog:list', 2),
( 6, 'monitor:online:forceLogout', '强制下线',         'monitor:online:list',  1),
-- OSS 配置
( 7, 'system:oss:query',           'OSS配置查询',      'system:oss:list',      1),
( 8, 'system:oss:add',             'OSS配置新增',      'system:oss:list',      2),
( 9, 'system:oss:edit',            'OSS配置修改',      'system:oss:list',      3),
(10, 'system:oss:remove',          'OSS配置删除',      'system:oss:list',      4),
-- 定时任务
(11, 'job:add',                    '任务新增',         'job:list',             1),
(12, 'job:edit',                   '任务修改',         'job:list',             2),
(13, 'job:remove',                 '任务删除',         'job:list',             3),
(14, 'job:log:remove',             '调度日志删除',     'job:log:list',         1),
-- 租户
(15, 'tenant:query',               '租户查询',         'tenant:list',          1),
(16, 'tenant:add',                 '租户新增',         'tenant:list',          2),
(17, 'tenant:edit',                '租户修改',         'tenant:list',          3),
(18, 'tenant:remove',              '租户删除',         'tenant:list',          4),
(19, 'system:tenant:list',         '租户计费查询',     'tenant:list',          5),
(20, 'system:tenant:edit',         '租户计费变更',     'tenant:list',          6),
(21, 'tenant:package:query',       '套餐查询',         'tenant:package:list',  1),
(22, 'tenant:package:add',         '套餐新增',         'tenant:package:list',  2),
(23, 'tenant:package:edit',        '套餐修改',         'tenant:package:list',  3),
(24, 'tenant:package:remove',      '套餐删除',         'tenant:package:list',  4),
(25, 'tenant:quota:edit',          '配额修改',         'tenant:quota:query',   1),
-- 开放平台
(26, 'open:app:query',             '应用查询',         'open:app:list',        1),
(27, 'open:app:add',               '应用新增',         'open:app:list',        2),
(28, 'open:app:edit',              '应用修改',         'open:app:list',        3),
(29, 'open:app:remove',            '应用删除',         'open:app:list',        4),
(30, 'open:app:resetSecret',       '重置密钥',         'open:app:list',        5),
-- AI 模型
(31, 'ai:model:query',             'AI模型查询',       'ai:model:list',        1),
(32, 'ai:model:add',               'AI模型新增',       'ai:model:list',        2),
(33, 'ai:model:edit',              'AI模型修改',       'ai:model:list',        3),
(34, 'ai:model:remove',            'AI模型删除',       'ai:model:list',        4),
(35, 'ai:model:test',              'AI模型连通性测试', 'ai:model:list',        5),
-- AI 知识库
(36, 'ai:kb:query',                '知识库查询',       'ai:kb:list',           1),
(37, 'ai:kb:add',                  '知识库新增',       'ai:kb:list',           2),
(38, 'ai:kb:edit',                 '知识库修改',       'ai:kb:list',           3),
(39, 'ai:kb:remove',               '知识库删除',       'ai:kb:list',           4),
(40, 'ai:kb:upload',               '知识库文档上传',   'ai:kb:list',           5),
-- AI MCP
(41, 'ai:mcp:query',               'MCP查询',          'ai:mcp:list',          1),
(42, 'ai:mcp:add',                 'MCP新增',          'ai:mcp:list',          2),
(43, 'ai:mcp:edit',                'MCP修改',          'ai:mcp:list',          3),
(44, 'ai:mcp:remove',              'MCP删除',          'ai:mcp:list',          4),
-- AI 智能体
(45, 'ai:agent:add',               '智能体新增',       'ai:agent:list',        1),
(46, 'ai:agent:edit',              '智能体修改',       'ai:agent:list',        2),
(47, 'ai:agent:remove',            '智能体删除',       'ai:agent:list',        3),
-- AI 工作流
(48, 'ai:workflow:add',            'AI工作流新增',     'ai:workflow:list',     1),
(49, 'ai:workflow:edit',           'AI工作流修改',     'ai:workflow:list',     2),
(50, 'ai:workflow:remove',         'AI工作流删除',     'ai:workflow:list',     3);

DROP PROCEDURE IF EXISTS han_up_20260812_seed_button;
DELIMITER $$
CREATE PROCEDURE han_up_20260812_seed_button()
BEGIN
    DECLARE v_seq               INT DEFAULT 1;
    DECLARE v_total             INT DEFAULT 0;
    DECLARE v_perms             VARCHAR(200);
    DECLARE v_name              VARCHAR(100);
    DECLARE v_parent_perms      VARCHAR(200);
    DECLARE v_sort              INT;
    DECLARE v_cnt               INT;
    DECLARE v_parent_id         BIGINT;
    DECLARE v_parent_ancestors  VARCHAR(500);
    DECLARE v_next_id           BIGINT;
    DECLARE v_added             INT DEFAULT 0;
    DECLARE v_exists            INT DEFAULT 0;
    DECLARE v_no_parent         INT DEFAULT 0;

    SELECT COUNT(*) INTO v_total FROM han_menu_button;

    button_loop: WHILE v_seq <= v_total DO
        SELECT perms, menu_name, parent_perms, sort_no
        INTO v_perms, v_name, v_parent_perms, v_sort
        FROM han_menu_button
        WHERE seq = v_seq;

        SET v_seq = v_seq + 1;

        SELECT COUNT(*) INTO v_cnt FROM sys_menu WHERE perms = v_perms;

        IF v_cnt > 0 THEN
            SET v_exists = v_exists + 1;
            ITERATE button_loop;
        END IF;

        SELECT MIN(id) INTO v_parent_id FROM sys_menu WHERE perms = v_parent_perms;

        IF v_parent_id IS NULL THEN
            SET v_no_parent = v_no_parent + 1;
            ITERATE button_loop;
        END IF;

        SELECT COALESCE(MAX(ancestors), '0') INTO v_parent_ancestors
        FROM sys_menu
        WHERE id = v_parent_id;

        SELECT COALESCE(MAX(id), 0) + 1 INTO v_next_id FROM sys_menu;

        INSERT INTO sys_menu (
            id, tenant_id, parent_id, ancestors, menu_name, menu_type,
            path, component, perms, icon, sort, visible, status
        )
        VALUES (
            v_next_id, NULL, v_parent_id, CONCAT(v_parent_ancestors, ',', v_parent_id),
            v_name, 'F', '', NULL, v_perms, '#', v_sort, 0, 0
        );

        SET v_added = v_added + 1;
        INSERT INTO han_upgrade_notice (notice_level, notice_scope, notice_text)
        VALUES ('INFO', '按钮权限', CONCAT('已新增按钮 ', v_name, '（perms=', v_perms, ', id=', v_next_id, '）'));
    END WHILE button_loop;

    INSERT INTO han_upgrade_notice (notice_level, notice_scope, notice_text)
    VALUES ('INFO', '汇总',
            CONCAT('按钮权限：新增 ', v_added, ' 个，已存在 ', v_exists,
                   ' 个，因父菜单不存在跳过 ', v_no_parent, ' 个（该档位未部署对应模块）'));
END $$
DELIMITER ;

CALL han_up_20260812_seed_button();
DROP PROCEDURE han_up_20260812_seed_button;

-- ---------------------------------------------
-- 5. 把本脚本涉及的权限点授予真实存在的超管角色
--    权限点清单与 PostgreSQL 版逐条一致。
--    PostgreSQL 版用 ON CONFLICT DO NOTHING；MySQL 这里用 INSERT IGNORE：
--    sys_role_menu 的主键就是 (role_id, menu_id)，两列都来自主键列，
--    唯一可能被忽略的错误就是重复主键，不存在被掩盖的其他错误。
-- ---------------------------------------------
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
CROSS JOIN sys_menu m
WHERE (r.id = 1 OR r.role_key IN ('admin', 'super_admin'))
  AND m.perms IN (
        'system:user:unbind',
        'monitor:operlog:list', 'monitor:operlog:export', 'monitor:operlog:remove',
        'monitor:loginlog:list', 'monitor:loginlog:export', 'monitor:loginlog:remove',
        'monitor:online:list', 'monitor:online:forceLogout',
        'monitor:server:list', 'monitor:cache:list',
        'system:oss:list', 'system:oss:query', 'system:oss:add', 'system:oss:edit', 'system:oss:remove',
        'job:list', 'job:add', 'job:edit', 'job:remove',
        'job:log:list', 'job:log:remove',
        'tenant:list', 'tenant:query', 'tenant:add', 'tenant:edit', 'tenant:remove',
        'system:tenant:list', 'system:tenant:edit',
        'tenant:package:list', 'tenant:package:query', 'tenant:package:add', 'tenant:package:edit', 'tenant:package:remove',
        'tenant:quota:query', 'tenant:quota:edit',
        'workflow:definition:list', 'workflow:instance:list', 'workflow:task:todo', 'workflow:task:done',
        'open:app:list', 'open:app:query', 'open:app:add', 'open:app:edit', 'open:app:remove', 'open:app:resetSecret',
        'ai:chat:list',
        'ai:model:list', 'ai:model:query', 'ai:model:add', 'ai:model:edit', 'ai:model:remove', 'ai:model:test',
        'ai:kb:list', 'ai:kb:query', 'ai:kb:add', 'ai:kb:edit', 'ai:kb:remove', 'ai:kb:upload',
        'ai:mcp:list', 'ai:mcp:query', 'ai:mcp:add', 'ai:mcp:edit', 'ai:mcp:remove',
        'ai:agent:list', 'ai:agent:add', 'ai:agent:edit', 'ai:agent:remove',
        'ai:workflow:list', 'ai:workflow:add', 'ai:workflow:edit', 'ai:workflow:remove',
        'ai:token:stats'
  );

SET @han_grant_rows = ROW_COUNT();

-- 目录菜单本身没有 perms，单独把它们也授给超管，否则子菜单在菜单树里挂不出来
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
CROSS JOIN sys_menu m
WHERE (r.id = 1 OR r.role_key IN ('admin', 'super_admin'))
  AND m.menu_type = 'M'
  AND m.path IN ('job', 'workflow', 'open', 'ai');

SET @han_grant_dir_rows = ROW_COUNT();

INSERT INTO han_upgrade_notice (notice_level, notice_scope, notice_text)
VALUES ('INFO', '汇总',
        CONCAT('超管授权：权限点新增 ', @han_grant_rows, ' 条，目录菜单新增 ', @han_grant_dir_rows, ' 条'));

-- ---------------------------------------------
-- 6. 输出执行报告并清理临时对象
--    下面这条 SELECT 是本脚本的执行结果，等价于 PostgreSQL 版的 RAISE NOTICE 输出。
--    出现 WARN 行说明有内容被跳过且需要人工处理。
-- ---------------------------------------------
SELECT notice_level, notice_scope, notice_text
FROM han_upgrade_notice
ORDER BY noted_at, notice_scope;

DROP TEMPORARY TABLE IF EXISTS han_perms_rename;
DROP TEMPORARY TABLE IF EXISTS han_menu_dir;
DROP TEMPORARY TABLE IF EXISTS han_menu_page;
DROP TEMPORARY TABLE IF EXISTS han_menu_button;
DROP TEMPORARY TABLE IF EXISTS han_upgrade_notice;
