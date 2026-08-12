-- 附中/巴蜀三课堂课程订购关系与授权物化（MySQL 8.4）
-- 依赖 20260811_education_master.sql 已建好 edu_school / edu_class / edu_subject / edu_semester / edu_room。
--
-- 手工执行时必须保留下面这行：客户端字符集缺省为 latin1 时，脚本里的中文会被双重编码成乱码。
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 1. 学期生命周期三态
-- ---------------------------------------------------------------------------
-- edu_* 全部表的 status 语义是「0 正常 / 1 停用」，不能拿来表达「未开始 / 进行中 / 已结束」。
-- 这里新开一列 lifecycle_status，并且刻意用字符串枚举而不是数字：
-- 数字 0 在本库里已经被 status 占用为「正常」，再出现一个含义为「未开始」的 0 必然被误读。
-- status          = 这条学期记录本身是否启用（0 正常 / 1 停用），沿用既有语义，不动。
-- lifecycle_status= 学期相对于当前日期处在哪个阶段，由定时任务按 begin_date / end_date 推进。
SET @ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE edu_semester ADD COLUMN lifecycle_status VARCHAR(16) NOT NULL DEFAULT ''NOT_STARTED''
             COMMENT ''学期阶段 NOT_STARTED/IN_PROGRESS/FINISHED，与表示启用状态的 status 无关'' AFTER current_flag',
        'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'edu_semester' AND COLUMN_NAME = 'lifecycle_status');
PREPARE alter_semester_lifecycle FROM @ddl;
EXECUTE alter_semester_lifecycle;
DEALLOCATE PREPARE alter_semester_lifecycle;

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE edu_semester ADD KEY idx_edu_semester_lifecycle (tenant_id, lifecycle_status, del_flag)',
        'SELECT 1')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'edu_semester' AND INDEX_NAME = 'idx_edu_semester_lifecycle');
PREPARE alter_semester_lifecycle_idx FROM @ddl;
EXECUTE alter_semester_lifecycle_idx;
DEALLOCATE PREPARE alter_semester_lifecycle_idx;

-- 存量数据按当天日期回填一次，之后交给定时任务维护。
UPDATE edu_semester
SET lifecycle_status = CASE
        WHEN CURDATE() < begin_date THEN 'NOT_STARTED'
        WHEN CURDATE() > end_date   THEN 'FINISHED'
        ELSE 'IN_PROGRESS'
    END
WHERE del_flag = 0;

-- ---------------------------------------------------------------------------
-- 2. 订购单主表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS edu_course_order (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    order_no VARCHAR(64) NOT NULL COMMENT '业务单号，兼作幂等键',
    listen_school_id BIGINT NOT NULL COMMENT '听讲学校，冗余自听讲班',
    listen_class_id BIGINT NOT NULL COMMENT '听讲教学班，订购最小单元',
    -- edu_class 上没有教室/设备绑定列，而 tb_course_attend 的 place_id 与 member_id 又必须有值
    -- （member_id 是听讲端设备编码，旧系统「加入课堂」事件按 (fk_course_id, member_id) 反查这一行）。
    -- 把这两项挂在订购单上：同一个听讲班在不同订购关系里本来就可能用不同教室。
    listen_room_id BIGINT NULL COMMENT '听讲教室，物化为 tb_course_attend.place_id',
    listen_device_id BIGINT NULL COMMENT '听讲端设备，物化为 tb_course_attend.member_id',
    lecture_school_id BIGINT NOT NULL COMMENT '主讲学校，冗余自主讲班',
    lecture_class_id BIGINT NOT NULL COMMENT '主讲教学班',
    semester_id BIGINT NOT NULL,
    grant_scope VARCHAR(16) NOT NULL COMMENT '授权粒度 WHOLE_CLASS 整班打包 / BY_SUBJECT 按科目',
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT'
        COMMENT 'DRAFT/PENDING/ACTIVE/FROZEN/EXPIRED/CANCELLED',
    effective_time DATETIME NULL COMMENT '缺省取学期 begin_date 当天 00:00:00',
    expire_time DATETIME NULL COMMENT '缺省取学期 end_date 当天 23:59:59',
    freeze_reason VARCHAR(200),
    cancel_reason VARCHAR(200),
    source_system VARCHAR(32) NOT NULL DEFAULT 'HAN',
    external_id VARCHAR(128),
    create_by BIGINT, create_name VARCHAR(50), create_dept BIGINT,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by BIGINT, update_name VARCHAR(50), update_time TIMESTAMP NULL,
    del_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500),
    -- 生成列，只为承载「一对班级一学期只能有一张有效单」这条唯一约束。
    -- MySQL 的唯一索引把多个 NULL 视为互不相同，所以已取消/已过期/已软删的历史单可以无限叠加，
    -- 而处于授权语义下的单子（PENDING/ACTIVE/FROZEN）只能有一张。约束由数据库保证，不靠应用层抢锁。
    -- 表达式里必须带 del_flag = 0：MyBatis-Plus 是逻辑删除，软删掉的单子若仍占着这个槽位，
    -- 同一对班级就再也建不出新单。
    active_flag TINYINT GENERATED ALWAYS AS (
        CASE WHEN del_flag = 0 AND status IN ('PENDING', 'ACTIVE', 'FROZEN') THEN 1 ELSE NULL END
    ) STORED,
    UNIQUE KEY uq_edu_course_order_no (tenant_id, order_no),
    UNIQUE KEY uq_edu_course_order_active (tenant_id, listen_class_id, lecture_class_id, semester_id, active_flag),
    KEY idx_edu_course_order_listen (tenant_id, listen_school_id, semester_id, status, del_flag),
    KEY idx_edu_course_order_lecture (tenant_id, lecture_class_id, semester_id, status, del_flag),
    KEY idx_edu_course_order_semester (tenant_id, semester_id, status, del_flag),
    CONSTRAINT chk_edu_course_order_dates
        CHECK (expire_time IS NULL OR effective_time IS NULL OR expire_time >= effective_time)
) COMMENT '听讲班对主讲班在某学期的收听授权凭据';

-- ---------------------------------------------------------------------------
-- 3. 订购科目明细（仅 BY_SUBJECT 粒度有行）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS edu_course_order_subject (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    subject_id BIGINT NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NULL,
    del_flag SMALLINT NOT NULL DEFAULT 0,
    UNIQUE KEY uq_edu_course_order_subject (tenant_id, order_id, subject_id),
    KEY idx_edu_course_order_subject_sub (tenant_id, subject_id, del_flag)
) COMMENT '按科目粒度订购单的科目明细，整班打包的单子此表必须为空';

-- ---------------------------------------------------------------------------
-- 4. 授权物化台账
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS edu_course_order_grant (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    course_id VARCHAR(64) NOT NULL COMMENT '三课堂 tb_course_info.course_id',
    course_name VARCHAR(200) NULL COMMENT '课程名称快照，台账页面直接展示，不回查旧库',
    -- 取消订购时要区分「未开始的课撤销、已结束的课保留回放」，
    -- 把上课时间快照在台账里，省掉逐条回查旧库。
    course_begin_time DATETIME NULL COMMENT '上课时间快照',
    listen_class_id BIGINT NOT NULL COMMENT '冗余自订购单，引用计数撤销要按听讲班聚合',
    subject_id BIGINT NULL COMMENT '课程科目，便于按科目撤销与核对',
    attend_id VARCHAR(64) NULL COMMENT '物化成功后回填的 tb_course_attend.attend_id',
    grant_status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
        COMMENT 'PENDING/MATERIALIZED/REVOKED/FAILED',
    suspended_flag TINYINT NOT NULL DEFAULT 0
        COMMENT '订购单冻结时置 1：台账仍是 MATERIALIZED，但听课记录已失效，不计入引用计数',
    attempt_count INT NOT NULL DEFAULT 0,
    last_error VARCHAR(500),
    last_attempt_time DATETIME NULL,
    materialized_time DATETIME NULL,
    revoked_time DATETIME NULL,
    create_by BIGINT, create_name VARCHAR(50), create_dept BIGINT,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by BIGINT, update_name VARCHAR(50), update_time TIMESTAMP NULL,
    del_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500),
    -- 同一张单对同一节课永远只有一行台账，重复同步只会更新不会新增。
    UNIQUE KEY uq_edu_course_order_grant (tenant_id, order_id, course_id),
    -- 「这节课当前还被哪些单授权给这个听讲班」的反查，引用计数撤销依赖它。
    KEY idx_edu_course_order_grant_course (tenant_id, course_id, listen_class_id, grant_status, suspended_flag),
    KEY idx_edu_course_order_grant_retry (tenant_id, grant_status, last_attempt_time),
    KEY idx_edu_course_order_grant_order (tenant_id, order_id, grant_status, del_flag)
) COMMENT '订购单到三课堂听课记录的物化台账';

-- ---------------------------------------------------------------------------
-- 5. 菜单与按钮权限
-- ---------------------------------------------------------------------------
-- 使用 2026081250xx 独立高位段，避开 20260811 教育主数据（2026081100xx）与其它在途脚本。
-- 全部按 perms 幂等，重复执行只校验不重复插入。

SET @edu_root_id := (SELECT MIN(id) FROM sys_menu WHERE perms = 'education:manage');

-- 5.1 学期与教室：有表有数据但一直缺菜单，只能靠 SQL 维护。订购单必须绑学期，这两项补齐。
INSERT INTO sys_menu (id, parent_id, ancestors, menu_name, menu_type, path, component, perms, icon, sort, visible, status)
SELECT source.id, @edu_root_id, CONCAT('0,', @edu_root_id), source.menu_name, 'C', source.path,
       source.component, source.perms, source.icon, source.sort, 0, 0
FROM (
    SELECT 202608125001 AS id, '学期管理' AS menu_name, 'semester' AS path, 'education/semester/index' AS component,
           'education:semester:list' AS perms, 'calendar' AS icon, 6 AS sort
    UNION ALL SELECT 202608125002, '教室管理', 'room', 'education/room/index', 'education:room:list', 'house', 7
) source
WHERE @edu_root_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu menu WHERE menu.perms = source.perms);

SET @edu_semester_id := (SELECT MIN(id) FROM sys_menu WHERE perms = 'education:semester:list');
SET @edu_room_id := (SELECT MIN(id) FROM sys_menu WHERE perms = 'education:room:list');

INSERT INTO sys_menu (id, parent_id, ancestors, menu_name, menu_type, path, component, perms, icon, sort, visible, status)
SELECT source.id, source.parent_id, CONCAT('0,', @edu_root_id, ',', source.parent_id), source.menu_name,
       'F', '', NULL, source.perms, '#', source.sort, 0, 0
FROM (
    SELECT 202608125011 AS id, @edu_semester_id AS parent_id, '学期新增' AS menu_name,
           'education:semester:add' AS perms, 1 AS sort
    UNION ALL SELECT 202608125012, @edu_semester_id, '学期修改', 'education:semester:edit', 2
    UNION ALL SELECT 202608125021, @edu_room_id, '教室新增', 'education:room:add', 1
    UNION ALL SELECT 202608125022, @edu_room_id, '教室修改', 'education:room:edit', 2
) source
WHERE source.parent_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu menu WHERE menu.perms = source.perms);

-- 5.2 课程订购
INSERT INTO sys_menu (id, parent_id, ancestors, menu_name, menu_type, path, component, perms, icon, sort, visible, status)
SELECT 202608125100, 0, '0', '课程订购', 'M', 'order', NULL, 'order:manage', 'shopping-cart', 6, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'order:manage');

SET @order_root_id := (SELECT MIN(id) FROM sys_menu WHERE perms = 'order:manage');

INSERT INTO sys_menu (id, parent_id, ancestors, menu_name, menu_type, path, component, perms, icon, sort, visible, status)
SELECT source.id, @order_root_id, CONCAT('0,', @order_root_id), source.menu_name, 'C', source.path,
       source.component, source.perms, source.icon, source.sort, 0, 0
FROM (
    SELECT 202608125101 AS id, '订购单管理' AS menu_name, 'course' AS path, 'order/course/index' AS component,
           'order:course:list' AS perms, 'tickets' AS icon, 1 AS sort
    UNION ALL SELECT 202608125102, '授权台账', 'grant', 'order/grant/index', 'order:grant:list', 'document-checked', 2
) source
WHERE @order_root_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu menu WHERE menu.perms = source.perms);

SET @order_course_id := (SELECT MIN(id) FROM sys_menu WHERE perms = 'order:course:list');
SET @order_grant_id := (SELECT MIN(id) FROM sys_menu WHERE perms = 'order:grant:list');

INSERT INTO sys_menu (id, parent_id, ancestors, menu_name, menu_type, path, component, perms, icon, sort, visible, status)
SELECT source.id, source.parent_id, CONCAT('0,', @order_root_id, ',', source.parent_id), source.menu_name,
       'F', '', NULL, source.perms, '#', source.sort, 0, 0
FROM (
    SELECT 202608125111 AS id, @order_course_id AS parent_id, '订购单新增' AS menu_name,
           'order:course:add' AS perms, 1 AS sort
    UNION ALL SELECT 202608125112, @order_course_id, '订购单修改', 'order:course:edit', 2
    UNION ALL SELECT 202608125113, @order_course_id, '订购单提交', 'order:course:submit', 3
    UNION ALL SELECT 202608125114, @order_course_id, '订购单冻结恢复', 'order:course:freeze', 4
    UNION ALL SELECT 202608125115, @order_course_id, '订购单取消', 'order:course:cancel', 5
    UNION ALL SELECT 202608125116, @order_course_id, '订购单同步授权', 'order:course:sync', 6
    UNION ALL SELECT 202608125121, @order_grant_id, '授权失败重试', 'order:grant:retry', 1
) source
WHERE source.parent_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu menu WHERE menu.perms = source.perms);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, menu.id
FROM sys_menu menu
WHERE (menu.perms LIKE 'order:%' OR menu.perms IN ('education:semester:list', 'education:semester:add',
       'education:semester:edit', 'education:room:list', 'education:room:add', 'education:room:edit'))
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu role_menu
      WHERE role_menu.role_id = 1 AND role_menu.menu_id = menu.id
  );
