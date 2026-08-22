-- 菜单权限分类：目录、页面、查询、操作。
-- 页面节点与列表/详情查询节点分离；角色仍通过 sys_role_menu 统一授权。
SET NAMES utf8mb4;
START TRANSACTION;

SET @permission_type_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_menu'
      AND column_name = 'permission_type'
);
SET @permission_type_sql := IF(
    @permission_type_exists = 0,
    'ALTER TABLE sys_menu ADD COLUMN permission_type VARCHAR(20) NULL COMMENT ''权限分类：DIRECTORY/PAGE/QUERY/OPERATION''',
    'SELECT 1'
);
PREPARE permission_type_stmt FROM @permission_type_sql;
EXECUTE permission_type_stmt;
DEALLOCATE PREPARE permission_type_stmt;

UPDATE sys_menu
SET permission_type = CASE
    WHEN menu_type = 'M' THEN 'DIRECTORY'
    WHEN menu_type = 'C' THEN 'PAGE'
    WHEN menu_type = 'F' AND perms REGEXP ':(list|query|info|detail)$' THEN 'QUERY'
    WHEN menu_type = 'F' THEN 'OPERATION'
    ELSE NULL
END
WHERE permission_type IS NULL OR permission_type = '';

-- 现有页面的列表权限迁移为同一页面下独立的查询节点。
UPDATE sys_menu
SET perms = NULL, permission_type = 'PAGE'
WHERE id IN (
    100,101,102,103,104,105,106,107,202608200009,
    200,201,202,203,204,300,400,401,500,
    202608110001,202608110002,202608110003,202608110004,202608110005,
    202608125001,202608125002,202608170001,202608170018,202608170014,
    202608170015,202608190101,202608125101,202608125102
);

INSERT INTO sys_menu
    (id, parent_id, ancestors, menu_name, menu_type, path, component, perms, icon, sort, visible, status, permission_type)
VALUES
  (202608210100,100,'0,1,100','用户列表','F',NULL,NULL,'system:user:list','#',99,0,0,'QUERY'),
  (202608210101,101,'0,1,101','角色列表','F',NULL,NULL,'system:role:list','#',99,0,0,'QUERY'),
  (202608210102,102,'0,1,102','菜单列表','F',NULL,NULL,'system:menu:list','#',99,0,0,'QUERY'),
  (202608210103,103,'0,1,103','部门列表','F',NULL,NULL,'system:dept:list','#',99,0,0,'QUERY'),
  (202608210104,104,'0,1,104','岗位列表','F',NULL,NULL,'system:post:list','#',99,0,0,'QUERY'),
  (202608210105,105,'0,1,105','字典列表','F',NULL,NULL,'system:dict:list','#',99,0,0,'QUERY'),
  (202608210106,106,'0,1,106','参数列表','F',NULL,NULL,'system:config:list','#',99,0,0,'QUERY'),
  (202608210107,107,'0,1,107','通知公告列表','F',NULL,NULL,'system:notice:list','#',99,0,0,'QUERY'),
  (202608210108,202608200009,'0,1,202608200009','系统设置查询','F',NULL,NULL,'system:brand:query','#',99,0,0,'QUERY'),
  (202608210109,200,'0,2,200','在线用户列表','F',NULL,NULL,'monitor:online:list','#',99,0,0,'QUERY'),
  (202608210110,201,'0,2,201','操作日志列表','F',NULL,NULL,'monitor:operlog:list','#',99,0,0,'QUERY'),
  (202608210111,202,'0,2,202','登录日志列表','F',NULL,NULL,'monitor:loginlog:list','#',99,0,0,'QUERY'),
  (202608210112,203,'0,2,203','缓存列表','F',NULL,NULL,'monitor:cache:list','#',99,0,0,'QUERY'),
  (202608210113,204,'0,2,204','服务监控查询','F',NULL,NULL,'monitor:server:list','#',99,0,0,'QUERY'),
  (202608210114,300,'0,3,300','代码生成列表','F',NULL,NULL,'tool:gen:list','#',99,0,0,'QUERY'),
  (202608210115,400,'0,4,400','租户列表查询','F',NULL,NULL,'system:tenant:list','#',99,0,0,'QUERY'),
  (202608210116,401,'0,4,401','套餐列表','F',NULL,NULL,'tenant:package:list','#',99,0,0,'QUERY'),
  (202608210117,500,'0,5,500','应用列表','F',NULL,NULL,'open:app:list','#',99,0,0,'QUERY'),
  (202608210118,202608110001,'0,202608110000,202608110001','学校列表','F',NULL,NULL,'education:school:list','#',99,0,0,'QUERY'),
  (202608210119,202608110002,'0,202608110000,202608110002','班级列表','F',NULL,NULL,'education:class:list','#',99,0,0,'QUERY'),
  (202608210120,202608110003,'0,202608110000,202608110003','客户端用户列表','F',NULL,NULL,'education:person:list','#',99,0,0,'QUERY'),
  (202608210121,202608110004,'0,202608110000,202608110004','科目列表','F',NULL,NULL,'education:subject:list','#',99,0,0,'QUERY'),
  (202608210122,202608110005,'0,202608110000,202608110005','设备列表','F',NULL,NULL,'education:device:list','#',99,0,0,'QUERY'),
  (202608210123,202608125001,'0,202608110000,202608125001','学期列表','F',NULL,NULL,'education:semester:list','#',99,0,0,'QUERY'),
  (202608210124,202608125002,'0,202608110000,202608125002','教室列表','F',NULL,NULL,'education:room:list','#',99,0,0,'QUERY'),
  (202608210125,202608170001,'0,202608110000,202608170001','学年列表','F',NULL,NULL,'education:academic-year:list','#',99,0,0,'QUERY'),
  (202608210126,202608170018,'0,202608110000,202608170018','区域列表','F',NULL,NULL,'education:region:list','#',99,0,0,'QUERY'),
  (202608210127,202608170014,'0,202608110000,202608170014','数据范围列表','F',NULL,NULL,'education:scope:list','#',99,0,0,'QUERY'),
  (202608210128,202608170015,'0,202608110000,202608170015','学年升级列表','F',NULL,NULL,'education:promotion:list','#',99,0,0,'QUERY'),
  (202608210129,202608190101,'0,202608110000,202608190101','课表节次列表','F',NULL,NULL,'education:course-rule:list','#',99,0,0,'QUERY'),
  (202608210130,202608125101,'0,202608125100,202608125101','订购单列表','F',NULL,NULL,'order:course:list','#',99,0,0,'QUERY'),
  (202608210131,202608125102,'0,202608125100,202608125102','授权台账列表','F',NULL,NULL,'order:grant:list','#',99,0,0,'QUERY')
ON DUPLICATE KEY UPDATE
  parent_id = VALUES(parent_id), ancestors = VALUES(ancestors), menu_name = VALUES(menu_name),
  menu_type = VALUES(menu_type), perms = VALUES(perms), permission_type = VALUES(permission_type),
  sort = VALUES(sort), visible = VALUES(visible), status = VALUES(status);

-- 已有角色保留原页面的查询能力；新角色可以只选页面或只选某类查询/操作。
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT role_id, 202608210100 FROM sys_role_menu WHERE menu_id = 100;
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT role_id, 202608210101 FROM sys_role_menu WHERE menu_id = 101;
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT role_id, 202608210102 FROM sys_role_menu WHERE menu_id = 102;
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT role_id, 202608210103 FROM sys_role_menu WHERE menu_id = 103;
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT role_id, 202608210104 FROM sys_role_menu WHERE menu_id = 104;
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT role_id, 202608210105 FROM sys_role_menu WHERE menu_id = 105;
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT role_id, 202608210106 FROM sys_role_menu WHERE menu_id = 106;
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT role_id, 202608210107 FROM sys_role_menu WHERE menu_id = 107;
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT role_id, 202608210108 FROM sys_role_menu WHERE menu_id = 202608200009;
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT role_id, 202608210109 FROM sys_role_menu WHERE menu_id = 200;
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT role_id, 202608210110 FROM sys_role_menu WHERE menu_id = 201;
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT role_id, 202608210111 FROM sys_role_menu WHERE menu_id = 202;
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT role_id, 202608210112 FROM sys_role_menu WHERE menu_id = 203;
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT role_id, 202608210113 FROM sys_role_menu WHERE menu_id = 204;
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT role_id, 202608210114 FROM sys_role_menu WHERE menu_id = 300;
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT role_id, 202608210115 FROM sys_role_menu WHERE menu_id = 400;
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT role_id, 202608210116 FROM sys_role_menu WHERE menu_id = 401;
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT role_id, 202608210117 FROM sys_role_menu WHERE menu_id = 500;
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT role_id, 202608210118 FROM sys_role_menu WHERE menu_id = 202608110001;
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT role_id, 202608210119 FROM sys_role_menu WHERE menu_id = 202608110002;
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT role_id, 202608210120 FROM sys_role_menu WHERE menu_id = 202608110003;
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT role_id, 202608210121 FROM sys_role_menu WHERE menu_id = 202608110004;
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT role_id, 202608210122 FROM sys_role_menu WHERE menu_id = 202608110005;
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT role_id, 202608210123 FROM sys_role_menu WHERE menu_id = 202608125001;
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT role_id, 202608210124 FROM sys_role_menu WHERE menu_id = 202608125002;
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT role_id, 202608210125 FROM sys_role_menu WHERE menu_id = 202608170001;
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT role_id, 202608210126 FROM sys_role_menu WHERE menu_id = 202608170018;
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT role_id, 202608210127 FROM sys_role_menu WHERE menu_id = 202608170014;
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT role_id, 202608210128 FROM sys_role_menu WHERE menu_id = 202608170015;
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT role_id, 202608210129 FROM sys_role_menu WHERE menu_id = 202608190101;
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT role_id, 202608210130 FROM sys_role_menu WHERE menu_id = 202608125101;
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT role_id, 202608210131 FROM sys_role_menu WHERE menu_id = 202608125102;

COMMIT;
