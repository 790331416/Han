-- =============================================
-- 20260812 唯一约束与逻辑删除对齐 + sys_user_social 结构兜底（幂等 · MySQL 8.4）
--
-- 对应 PostgreSQL 版：sql/upgrades/postgres/20260812_unique_constraint_del_flag_alignment.sql
--
-- 背景：
--   BaseEntity.delFlag 标了 @TableLogic，删除是逻辑删除。但唯一约束都建在业务列上、
--   不含 del_flag，软删除一条记录后就再也建不出同名记录。
--   MySQL 侧的存量库只可能来自 2026-08-11 首版 sql/tiers/small/small-init-mysql.sql：
--   那一版把 sys_user 的唯一性写成建表内联 UNIQUE (username, tenant_id)、把 sys_client
--   写成 client_key ... UNIQUE，sys_role / sys_post / sys_dict_type / sys_dict_data /
--   sys_config 则完全没有唯一约束。本脚本把这批唯一性统一换成「排除逻辑删除行」的唯一索引，
--   口径与 sql/tiers/small/small-init-mysql.sql「22. 唯一约束」段完全一致。
--
--   与 PostgreSQL 版的一处范围差异（有意为之）：本脚本多处理一张 sys_user。
--   PostgreSQL 侧 sys_user 由 sql/upgrades/postgres/phase5_unique_constraint.sql 处理过，
--   那个脚本早于 MySQL 引入（2026-08-11），按 sql/upgrades/mysql/README.md 的通道约定不回港；
--   而 MySQL 首版初始化恰好把 sys_user 的旧写法烘焙进了存量库，只能在本脚本里补上。
--
-- 幂等策略（全文只用这一种风格）：
--   1. MySQL 8.4 没有 PostgreSQL 的匿名代码块，也不支持 CREATE INDEX / DROP INDEX 的
--      IF [NOT] EXISTS，所以全部存在性判断都查 information_schema，再由
--      「临时表存规格 + 存储过程 WHILE 遍历 + PREPARE/EXECUTE 执行动态 DDL」落地。
--      存储过程与临时表在脚本末尾全部删除，库里不留常驻对象。
--   2. 不用游标：游标的 NOT FOUND 处理器会被循环体里任何「查不到行的 SELECT ... INTO」
--      提前触发，把循环静默截断。这里改成按连续 seq 顺序取行，且所有取值 SELECT 一律走
--      聚合函数（COUNT / MIN / MAX / GROUP_CONCAT），保证永远返回且只返回一行。
--   3. 旧唯一约束按「索引的每个键部件都落在规格键列集合内」从 information_schema.statistics
--      查出来再删，不依赖固定约束名。MySQL 没有独立于索引的唯一约束对象，UNIQUE 约束
--      就是唯一索引，因此这里查索引而不是查 table_constraints。
--   4. 含函数式键部件的索引在 information_schema.statistics 里 column_name 为 NULL，
--      不会被判成「旧约束」，所以本脚本自己建出来的索引重复执行时不会被删掉重建。
--   5. 目标索引名已存在时一律不动（与 PostgreSQL 版一致），只记一条说明；若它不含
--      del_flag 函数式键部件（medium/full 的 MySQL 初始化就是这种口径），记 WARN 提示差异。
--   6. 表不存在、关键列缺失时跳过，兼容 small/medium 档位和缺列旧库。
--   7. 建索引前先查重复；有重复时跳过，不让整条升级链中断。
--      **有重复的环境需要人工清理后重跑本脚本。**
--   8. PostgreSQL 的 RAISE NOTICE 在 MySQL 没有对应物：所有删除、新建、跳过都写进会话
--      临时表 han_upgrade_notice，脚本最后一条语句把它整表 SELECT 出来。任何跳过都会
--      出现在结果集里，不会静默。
--
-- 执行方式：
--   本文件包含存储过程，必须用支持 DELIMITER 的客户端执行（mysql 命令行、
--   MySQL Workbench、DBeaver 均可）：
--     mysql --default-character-set=utf8mb4 -h <host> -P <port> -u <user> -p <db> < 本文件
--   需要的权限：SELECT / INSERT / UPDATE / ALTER / INDEX / CREATE / CREATE ROUTINE /
--   ALTER ROUTINE / EXECUTE / CREATE TEMPORARY TABLES。
--
-- 回滚：
--   MySQL 的 DDL 自动提交、不能事务回滚，只能反向执行：
--     ALTER TABLE sys_user      DROP INDEX sys_user_username_tenant_uniq;
--     ALTER TABLE sys_role      DROP INDEX uk_sys_role_key_tenant;
--     ALTER TABLE sys_role      DROP INDEX uk_sys_role_name_tenant;
--     ALTER TABLE sys_post      DROP INDEX uk_sys_post_code_tenant;
--     ALTER TABLE sys_dict_type DROP INDEX uk_sys_dict_type_tenant;
--     ALTER TABLE sys_dict_data DROP INDEX uk_sys_dict_data_tenant;
--     ALTER TABLE sys_config    DROP INDEX uk_sys_config_key_tenant;
--     ALTER TABLE sys_client    DROP INDEX uk_sys_client_key;
--   被本脚本删掉的旧唯一约束不会自动恢复，需要时按原列集合手工重建：
--     ALTER TABLE sys_user   ADD UNIQUE KEY username (username, tenant_id);
--     ALTER TABLE sys_client ADD UNIQUE KEY client_key (client_key);
--   sys_user_social 若是本脚本新建的，回滚为 DROP TABLE sys_user_social;（会丢数据，慎用）
-- =============================================

SET NAMES utf8mb4;

-- 下面要用 GROUP_CONCAT 把「一次删多个索引」拼成一条 ALTER 语句，默认 1024 字节上限
-- 在索引名较长时会被静默截断，拼出半截 SQL，这里放宽一档。
SET SESSION group_concat_max_len = 8192;

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
-- 1. sys_user_social 结构兜底
--    2026-08-11 首版 small-init-mysql.sql（ef2aa26）根本没有这张表，
--    同日的 39b7574 才补上。两个版本之间建的库缺表，这里补建。
--    结构取 sql/tiers/small/small-init-mysql.sql 的最终形态：
--    tenant_scope 生成列 + 两个租户隔离唯一键，而不是 PostgreSQL 版那种
--    「先建全局 UNIQUE(provider, open_id) 再换掉」的中间形态——MySQL 侧从来没有
--    存在过那个中间形态，直接建终态更安全。
-- ---------------------------------------------
CREATE TABLE IF NOT EXISTS sys_user_social (
    id              BIGINT          NOT NULL PRIMARY KEY,
    user_id         BIGINT          NOT NULL,
    tenant_id       BIGINT,
    tenant_scope    BIGINT          GENERATED ALWAYS AS (COALESCE(tenant_id, 0)) STORED,
    provider        VARCHAR(32)     NOT NULL,
    open_id         VARCHAR(128)    NOT NULL,
    access_token    VARCHAR(512),
    nickname        VARCHAR(100),
    avatar          VARCHAR(500),
    extra           TEXT,
    create_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_social_user_id (user_id),
    INDEX idx_user_social_provider_openid (provider, open_id),
    UNIQUE KEY uq_user_social_tenant_provider_openid (tenant_scope, provider, open_id),
    UNIQUE KEY uq_user_social_user_provider (user_id, provider)
);

DROP PROCEDURE IF EXISTS han_up_20260812_social_guard;
DELIMITER $$
CREATE PROCEDURE han_up_20260812_social_guard()
BEGIN
    DECLARE v_cnt INT DEFAULT 0;

    SELECT COUNT(*) INTO v_cnt
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_user_social'
      AND index_name = 'idx_user_social_user_id';

    IF v_cnt = 0 THEN
        CREATE INDEX idx_user_social_user_id ON sys_user_social (user_id);
        INSERT INTO han_upgrade_notice (notice_level, notice_scope, notice_text)
        VALUES ('INFO', 'sys_user_social', '已补建索引 idx_user_social_user_id');
    END IF;

    SELECT COUNT(*) INTO v_cnt
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_user_social'
      AND index_name = 'idx_user_social_provider_openid';

    IF v_cnt = 0 THEN
        CREATE INDEX idx_user_social_provider_openid ON sys_user_social (provider, open_id);
        INSERT INTO han_upgrade_notice (notice_level, notice_scope, notice_text)
        VALUES ('INFO', 'sys_user_social', '已补建索引 idx_user_social_provider_openid');
    END IF;

    -- tenant_scope 是租户隔离唯一键的组成列。缺列说明这张表来自本仓库之外的建表语句，
    -- 补列要重建整表且可能撞上已有重复数据，这里只报警不自动改。
    SELECT COUNT(*) INTO v_cnt
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_user_social'
      AND column_name = 'tenant_scope';

    IF v_cnt = 0 THEN
        INSERT INTO han_upgrade_notice (notice_level, notice_scope, notice_text)
        VALUES ('WARN', 'sys_user_social',
                '缺少 tenant_scope 生成列，结构与 sql/tiers/small/small-init-mysql.sql 不一致，需人工比对后补列');
    END IF;
END $$
DELIMITER ;

CALL han_up_20260812_social_guard();
DROP PROCEDURE han_up_20260812_social_guard;

-- ---------------------------------------------
-- 2. 把不带 del_flag 条件的唯一约束换成「排除逻辑删除行」的唯一索引
--
--    PostgreSQL 用部分唯一索引表达：CREATE UNIQUE INDEX ... WHERE del_flag = 0。
--    MySQL 8.4 的索引不支持 WHERE 条件，改用函数式键部件 (IF(del_flag = 0, 0, NULL))：
--    未删除行该键为 0 参与唯一性判定，已删除行该键为 NULL，而唯一索引允许多个 NULL，
--    因此软删除后可以重建同名记录。口径与 small-init-mysql.sql「22. 唯一约束」段一致。
--
--    规格表字段说明：
--      idx_cols  建索引用的键部件列表（含函数式键部件）
--      dup_cols  查重复用的 GROUP BY 表达式（不含 del_flag，重复判定只看未删除行）
--      key_cols  用于识别「旧唯一约束」的普通列集合，逗号分隔且不能带空格（FIND_IN_SET 不裁剪空白）
-- ---------------------------------------------
DROP TEMPORARY TABLE IF EXISTS han_uk_spec;
CREATE TEMPORARY TABLE han_uk_spec (
    seq         INT             NOT NULL PRIMARY KEY,
    tbl         VARCHAR(64)     NOT NULL,
    idx         VARCHAR(64)     NOT NULL,
    idx_cols    VARCHAR(500)    NOT NULL,
    dup_cols    VARCHAR(500)    NOT NULL,
    key_cols    VARCHAR(500)    NOT NULL
) DEFAULT CHARSET = utf8mb4;

INSERT INTO han_uk_spec (seq, tbl, idx, idx_cols, dup_cols, key_cols) VALUES
-- sys_user 是 MySQL 通道特有的一条：PostgreSQL 侧由 phase5_unique_constraint.sql 处理，
-- 那个脚本按通道约定不回港，而 2026-08-11 首版 MySQL 初始化把旧写法烘焙进了存量库。
(1, 'sys_user', 'sys_user_username_tenant_uniq',
    'username, tenant_id, (IF(del_flag = 0, 0, NULL))',
    'username, tenant_id',
    'username,tenant_id'),
(2, 'sys_role', 'uk_sys_role_key_tenant',
    'tenant_id, role_key, (IF(del_flag = 0, 0, NULL))',
    'tenant_id, role_key',
    'tenant_id,role_key'),
(3, 'sys_role', 'uk_sys_role_name_tenant',
    'tenant_id, role_name, (IF(del_flag = 0, 0, NULL))',
    'tenant_id, role_name',
    'tenant_id,role_name'),
(4, 'sys_post', 'uk_sys_post_code_tenant',
    'tenant_id, post_code, (IF(del_flag = 0, 0, NULL))',
    'tenant_id, post_code',
    'tenant_id,post_code'),
(5, 'sys_dict_type', 'uk_sys_dict_type_tenant',
    '(COALESCE(tenant_id, 0)), dict_type, (IF(del_flag = 0, 0, NULL))',
    'COALESCE(tenant_id, 0), dict_type',
    'tenant_id,dict_type'),
(6, 'sys_dict_data', 'uk_sys_dict_data_tenant',
    '(COALESCE(tenant_id, 0)), dict_type, dict_value, (IF(del_flag = 0, 0, NULL))',
    'COALESCE(tenant_id, 0), dict_type, dict_value',
    'tenant_id,dict_type,dict_value'),
(7, 'sys_config', 'uk_sys_config_key_tenant',
    '(COALESCE(tenant_id, 0)), config_key, (IF(del_flag = 0, 0, NULL))',
    'COALESCE(tenant_id, 0), config_key',
    'tenant_id,config_key'),
(8, 'sys_client', 'uk_sys_client_key',
    'client_key, (IF(del_flag = 0, 0, NULL))',
    'client_key',
    'client_key');

DROP PROCEDURE IF EXISTS han_up_20260812_align_unique;
DELIMITER $$
CREATE PROCEDURE han_up_20260812_align_unique()
BEGIN
    DECLARE v_seq           INT DEFAULT 1;
    DECLARE v_total         INT DEFAULT 0;
    DECLARE v_tbl           VARCHAR(64);
    DECLARE v_idx           VARCHAR(64);
    DECLARE v_idx_cols      VARCHAR(500);
    DECLARE v_dup_cols      VARCHAR(500);
    DECLARE v_key_cols      VARCHAR(500);
    DECLARE v_need_cols     VARCHAR(600);
    DECLARE v_need_cnt      INT;
    DECLARE v_found_cnt     INT;
    DECLARE v_found_cols    VARCHAR(600);
    DECLARE v_cnt           INT;
    DECLARE v_expr_parts    INT;
    DECLARE v_drop_sql      VARCHAR(2000);
    DECLARE v_drop_names    VARCHAR(1000);
    DECLARE v_created       INT DEFAULT 0;
    DECLARE v_kept          INT DEFAULT 0;
    DECLARE v_skipped       INT DEFAULT 0;

    SELECT COUNT(*) INTO v_total FROM han_uk_spec;

    spec_loop: WHILE v_seq <= v_total DO
        SELECT tbl, idx, idx_cols, dup_cols, key_cols
        INTO v_tbl, v_idx, v_idx_cols, v_dup_cols, v_key_cols
        FROM han_uk_spec
        WHERE seq = v_seq;

        -- 先自增，后面任何一处 ITERATE 都不会把循环卡死
        SET v_seq = v_seq + 1;

        -- 表不存在：整条规格跳过（small/medium 档位没有部分表）
        SELECT COUNT(*) INTO v_cnt
        FROM information_schema.tables
        WHERE table_schema = DATABASE() AND table_name = v_tbl;

        IF v_cnt = 0 THEN
            INSERT INTO han_upgrade_notice (notice_level, notice_scope, notice_text)
            VALUES ('INFO', v_idx, CONCAT('跳过：表 ', v_tbl, ' 不存在，本档位没有该模块'));
            SET v_skipped = v_skipped + 1;
            ITERATE spec_loop;
        END IF;

        -- 关键列或 del_flag 缺失：跳过（兼容缺列旧库）
        SET v_need_cols = CONCAT(v_key_cols, ',del_flag');
        SET v_need_cnt = LENGTH(v_need_cols) - LENGTH(REPLACE(v_need_cols, ',', '')) + 1;

        SELECT COUNT(*), GROUP_CONCAT(column_name ORDER BY column_name SEPARATOR ',')
        INTO v_found_cnt, v_found_cols
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = v_tbl
          AND FIND_IN_SET(column_name, v_need_cols) > 0;

        IF v_found_cnt < v_need_cnt THEN
            INSERT INTO han_upgrade_notice (notice_level, notice_scope, notice_text)
            VALUES ('WARN', v_idx,
                    CONCAT('跳过：', v_tbl, ' 缺列，需要 ', v_need_cols,
                           '，实际只有 ', COALESCE(v_found_cols, '（一个都没有）')));
            SET v_skipped = v_skipped + 1;
            ITERATE spec_loop;
        END IF;

        -- 旧唯一约束：列集合落在规格键列内、且不是目标索引本身的那些唯一索引。
        -- 含函数式键部件的索引 column_name 为 NULL，FIND_IN_SET 返回 NULL 走 ELSE 分支，
        -- SUM 不为 0，因此天然被排除，本脚本建出来的索引不会被自己删掉。
        SELECT GROUP_CONCAT(CONCAT('DROP INDEX `', index_name, '`') SEPARATOR ', '),
               GROUP_CONCAT(index_name SEPARATOR ', ')
        INTO v_drop_sql, v_drop_names
        FROM (
            SELECT index_name
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = v_tbl
              AND non_unique = 0
              AND index_name <> 'PRIMARY'
              AND index_name <> v_idx
            GROUP BY index_name
            HAVING SUM(CASE WHEN FIND_IN_SET(column_name, v_key_cols) > 0 THEN 0 ELSE 1 END) = 0
        ) AS legacy_idx;

        IF v_drop_sql IS NOT NULL THEN
            SET @han_uk_sql = CONCAT('ALTER TABLE `', v_tbl, '` ', v_drop_sql);
            PREPARE han_uk_stmt FROM @han_uk_sql;
            EXECUTE han_uk_stmt;
            DEALLOCATE PREPARE han_uk_stmt;

            INSERT INTO han_upgrade_notice (notice_level, notice_scope, notice_text)
            VALUES ('INFO', v_idx, CONCAT('已删除 ', v_tbl, ' 上不含 del_flag 的旧唯一约束：', v_drop_names));
        END IF;

        -- 目标索引名已存在：不动它（与 PostgreSQL 版一致）
        SELECT COUNT(*), COUNT(expression)
        INTO v_cnt, v_expr_parts
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = v_tbl
          AND index_name = v_idx;

        IF v_cnt > 0 THEN
            SET v_kept = v_kept + 1;
            IF v_expr_parts = 0 THEN
                -- medium/full 的 MySQL 初始化脚本就是这种口径：索引名一致但没有 del_flag 键部件。
                -- 自动重建会让升级出来的库和 init 脚本对不上，这里只报警，交人工决定。
                INSERT INTO han_upgrade_notice (notice_level, notice_scope, notice_text)
                VALUES ('WARN', v_idx,
                        CONCAT(v_tbl, ' 上同名索引已存在但不含 del_flag 函数式键部件，未改动；',
                               '该表软删除后仍无法重建同名记录，如需对齐 small 口径请人工重建索引'));
            ELSE
                INSERT INTO han_upgrade_notice (notice_level, notice_scope, notice_text)
                VALUES ('INFO', v_idx, CONCAT(v_tbl, ' 上目标唯一索引已存在，未改动'));
            END IF;
            ITERATE spec_loop;
        END IF;

        -- 建索引前查重复：只看未删除行，分组表达式与索引键部件一致
        SET @han_uk_dup = 0;
        SET @han_uk_sql = CONCAT(
            'SELECT COUNT(*) INTO @han_uk_dup FROM (SELECT 1 FROM `', v_tbl,
            '` WHERE COALESCE(del_flag, 0) = 0 GROUP BY ', v_dup_cols,
            ' HAVING COUNT(*) > 1) AS dup_grp');
        PREPARE han_uk_stmt FROM @han_uk_sql;
        EXECUTE han_uk_stmt;
        DEALLOCATE PREPARE han_uk_stmt;

        IF @han_uk_dup > 0 THEN
            INSERT INTO han_upgrade_notice (notice_level, notice_scope, notice_text)
            VALUES ('WARN', v_idx,
                    CONCAT('跳过：', v_tbl, ' 按 ', v_dup_cols, ' 分组存在 ', @han_uk_dup,
                           ' 组重复数据，请人工清理后重跑本脚本；',
                           '注意旧唯一约束若已在本次执行中删除，重跑前该表处于无唯一性保护状态'));
            SET v_skipped = v_skipped + 1;
            ITERATE spec_loop;
        END IF;

        SET @han_uk_sql = CONCAT('CREATE UNIQUE INDEX `', v_idx, '` ON `', v_tbl, '` (', v_idx_cols, ')');
        PREPARE han_uk_stmt FROM @han_uk_sql;
        EXECUTE han_uk_stmt;
        DEALLOCATE PREPARE han_uk_stmt;

        SET v_created = v_created + 1;
        INSERT INTO han_upgrade_notice (notice_level, notice_scope, notice_text)
        VALUES ('INFO', v_idx, CONCAT('已在 ', v_tbl, ' 上新建唯一索引：', v_idx_cols));
    END WHILE spec_loop;

    INSERT INTO han_upgrade_notice (notice_level, notice_scope, notice_text)
    VALUES ('INFO', '汇总',
            CONCAT('唯一索引对齐完成：新建 ', v_created, ' 个，已存在 ', v_kept, ' 个，跳过 ', v_skipped, ' 个'));
END $$
DELIMITER ;

CALL han_up_20260812_align_unique();
DROP PROCEDURE han_up_20260812_align_unique;

-- ---------------------------------------------
-- 3. 输出执行报告并清理临时对象
--    下面这条 SELECT 是本脚本的执行结果，等价于 PostgreSQL 版的 RAISE NOTICE 输出。
--    只要出现 WARN 行，就说明有规格被跳过，需要人工处理后重跑。
-- ---------------------------------------------
SELECT notice_level, notice_scope, notice_text
FROM han_upgrade_notice
ORDER BY noted_at, notice_scope;

DROP TEMPORARY TABLE IF EXISTS han_uk_spec;
DROP TEMPORARY TABLE IF EXISTS han_upgrade_notice;
