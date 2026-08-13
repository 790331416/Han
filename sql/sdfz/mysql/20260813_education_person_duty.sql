-- 教育人员的校内岗位维度（MySQL 8.4）
--
-- 必须保留下面这行：手工用 mysql 客户端执行时，若会话字符集不是 utf8mb4（常见缺省是 latin1），
-- 脚本里的中文注释与默认值会被按客户端默认字符集二次编码后入库，表现为乱码。
SET NAMES utf8mb4;

-- 背景：旧三课堂前端把两个不同维度都叫 roleType。
--   身份类型 roles[].roleType        —— 登录过滤看它，教师必须是 2；
--   岗位     dutyType[].roleType     —— 控制台菜单按 isSchool + '-' + 岗位码 授权。
-- 此前管理端只有身份类型，岗位字段被塞了身份类型码（拼出 2-2），
-- 于是课程预约、授课统计、学校设置、学校直播间、学校结对五个校级页面没有任何账号进得去。
--
-- 这里补的是缺的那个维度本身：
--   duty_code = 'TEACHER'      普通教师，映射旧岗位码 3（2-3，不命中任何校级菜单）
--   duty_code = 'SCHOOL_ADMIN' 校级管理员，映射旧岗位码 1（2-1，命中上述五个页面）
-- 岗位码映射在配置项 sdfz.compat.duty-type 里，不写死在库里。
--
-- 存量数据一律回填成 TEACHER：岗位维度是新增的，此前没有任何人被授予过管理岗，
-- 回填成管理岗等于给全校教师默认发权限。校级管理员必须由管理员在人员表单里逐个授予。
--
-- 幂等：列已存在时跳过 ALTER，回填只覆盖空值。

SET @has_duty_code := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'edu_person' AND COLUMN_NAME = 'duty_code');

SET @ddl := IF(@has_duty_code > 0,
    'SELECT ''edu_person.duty_code 已存在，跳过'' AS skipped',
    'ALTER TABLE edu_person ADD COLUMN duty_code varchar(32) COLLATE utf8mb4_unicode_ci NULL
        COMMENT ''校内岗位：TEACHER=普通教师 SCHOOL_ADMIN=校级管理员，为空按普通教师处理''
        AFTER person_type');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE edu_person SET duty_code = 'TEACHER'
WHERE duty_code IS NULL OR duty_code = '';

-- 学生不参与岗位授权，但列上留 NULL 会让后续查询要处处判空，统一回填成 TEACHER，
-- 服务端在写入侧已拒绝给学生配置非普通教师岗位。
