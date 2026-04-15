-- 本文件由仓库整理脚本从现有 PostgreSQL 正式脚本拆分生成。
-- 如需调整结构，请同步更新 manifest 与 sql/README.md。

-- 3. 部门
INSERT INTO sys_dept (id, tenant_id, parent_id, ancestors, dept_name, dept_code, sort, status) VALUES
(100, 1, 0, '0', 'han科技', 'HQ', 0, 0),
(101, 1, 100, '0,100', '研发部门', 'RD', 1, 0),
(102, 1, 100, '0,100', '产品部门', 'PD', 2, 0),
(103, 1, 100, '0,100', '运营部门', 'OP', 3, 0),
(104, 1, 101, '0,100,101', '研发一组', 'RD1', 1, 0),
(105, 1, 101, '0,100,101', '研发二组', 'RD2', 2, 0);

-- 4. 用户 (密码: admin123)
INSERT INTO sys_user (id, tenant_id, dept_id, username, nickname, password, phone, status, remark) VALUES
(1, 1, 100, 'admin', '超级管理员', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '13800000000', 0, '系统超级管理员'),
(2, 1, 101, 'han', '徐漫', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '13800000001', 0, '普通管理员');

-- 5. 岗位
INSERT INTO sys_post (id, tenant_id, post_code, post_name, post_sort, status) VALUES
(1, 1, 'ceo', '董事长', 1, 0),
(2, 1, 'cto', '技术总监', 2, 0),
(3, 1, 'manager', '项目经理', 3, 0),
(4, 1, 'developer', '开发工程师', 4, 0);

-- 6. 角色
INSERT INTO sys_role (id, tenant_id, role_name, role_key, role_sort, data_scope, status, remark) VALUES
(1, 1, '超级管理员', 'admin', 1, '1', 0, '拥有全部权限'),
(2, 1, '普通管理员', 'common', 2, '2', 0, '普通管理员角色'),
(3, 1, '部门管理员', 'dept_admin', 3, '3', 0, '本部门数据权限'),
(4, 1, '普通用户', 'user', 4, '5', 0, '仅本人数据权限');

-- 7. 菜单
INSERT INTO sys_menu (id, parent_id, ancestors, menu_name, menu_type, path, component, perms, icon, sort, visible, status) VALUES
(1, 0, '0', '系统管理', 'M', 'system', NULL, NULL, 'system', 1, 0, 0),
(2, 0, '0', '系统监控', 'M', 'monitor', NULL, NULL, 'monitor', 2, 0, 0),
(3, 0, '0', '系统工具', 'M', 'tool', NULL, NULL, 'tool', 3, 0, 0),
(4, 0, '0', '租户管理', 'M', 'tenant', NULL, NULL, 'peoples', 4, 0, 0);

INSERT INTO sys_menu (id, parent_id, ancestors, menu_name, menu_type, path, component, perms, icon, sort, visible, status) VALUES
(100, 1, '0,1', '用户管理', 'C', 'user', 'system/user/index', 'system:user:list', 'user', 1, 0, 0),
(101, 1, '0,1', '角色管理', 'C', 'role', 'system/role/index', 'system:role:list', 'peoples', 2, 0, 0),
(102, 1, '0,1', '菜单管理', 'C', 'menu', 'system/menu/index', 'system:menu:list', 'tree-table', 3, 0, 0),
(103, 1, '0,1', '部门管理', 'C', 'dept', 'system/dept/index', 'system:dept:list', 'tree', 4, 0, 0),
(104, 1, '0,1', '岗位管理', 'C', 'post', 'system/post/index', 'system:post:list', 'post', 5, 0, 0),
(105, 1, '0,1', '字典管理', 'C', 'dict', 'system/dict/index', 'system:dict:list', 'dict', 6, 0, 0),
(106, 1, '0,1', '参数设置', 'C', 'config', 'system/config/index', 'system:config:list', 'edit', 7, 0, 0),
(107, 1, '0,1', '通知公告', 'C', 'notice', 'system/notice/index', 'system:notice:list', 'message', 8, 0, 0),
(108, 1, '0,1', '客户端管理', 'C', 'client', 'system/client/index', 'system:client:list', 'client', 9, 0, 0);

INSERT INTO sys_menu (id, parent_id, ancestors, menu_name, menu_type, path, component, perms, icon, sort, visible, status) VALUES
(200, 2, '0,2', '在线用户', 'C', 'online', 'monitor/online/index', 'monitor:online:list', 'online', 1, 0, 0),
(201, 2, '0,2', '操作日志', 'C', 'operlog', 'monitor/operlog/index', 'monitor:operlog:list', 'form', 2, 0, 0),
(202, 2, '0,2', '登录日志', 'C', 'loginlog', 'monitor/loginlog/index', 'monitor:loginlog:list', 'logininfor', 3, 0, 0),
(203, 2, '0,2', '缓存监控', 'C', 'cache', 'monitor/cache/index', 'monitor:cache:list', 'redis', 4, 0, 0),
(204, 2, '0,2', '服务监控', 'C', 'server', 'monitor/server/index', 'monitor:server:list', 'server', 5, 0, 0);

INSERT INTO sys_menu (id, parent_id, ancestors, menu_name, menu_type, path, component, perms, icon, sort, visible, status) VALUES
(300, 3, '0,3', '代码生成', 'C', 'gen', 'tool/gen/index', 'tool:gen:list', 'code', 1, 0, 0),
(301, 3, '0,3', '系统接口', 'C', 'swagger', 'tool/swagger/index', 'tool:swagger:list', 'swagger', 2, 0, 0);

INSERT INTO sys_menu (id, parent_id, ancestors, menu_name, menu_type, path, component, perms, icon, sort, visible, status) VALUES
(400, 4, '0,4', '租户列表', 'C', 'list', 'tenant/list/index', 'tenant:list', 'list', 1, 0, 0),
(401, 4, '0,4', '套餐管理', 'C', 'package', 'tenant/package/index', 'tenant:package:list', 'component', 2, 0, 0);

-- 按钮权限
INSERT INTO sys_menu (id, parent_id, ancestors, menu_name, menu_type, path, component, perms, icon, sort, visible, status) VALUES
(1001, 100, '0,1,100', '用户查询', 'F', '', NULL, 'system:user:query', '#', 1, 0, 0),
(1002, 100, '0,1,100', '用户新增', 'F', '', NULL, 'system:user:add', '#', 2, 0, 0),
(1003, 100, '0,1,100', '用户修改', 'F', '', NULL, 'system:user:edit', '#', 3, 0, 0),
(1004, 100, '0,1,100', '用户删除', 'F', '', NULL, 'system:user:remove', '#', 4, 0, 0),
(1005, 100, '0,1,100', '用户导出', 'F', '', NULL, 'system:user:export', '#', 5, 0, 0),
(1006, 100, '0,1,100', '用户导入', 'F', '', NULL, 'system:user:import', '#', 6, 0, 0),
(1007, 100, '0,1,100', '重置密码', 'F', '', NULL, 'system:user:resetPwd', '#', 7, 0, 0),
(1011, 101, '0,1,101', '角色查询', 'F', '', NULL, 'system:role:query', '#', 1, 0, 0),
(1012, 101, '0,1,101', '角色新增', 'F', '', NULL, 'system:role:add', '#', 2, 0, 0),
(1013, 101, '0,1,101', '角色修改', 'F', '', NULL, 'system:role:edit', '#', 3, 0, 0),
(1014, 101, '0,1,101', '角色删除', 'F', '', NULL, 'system:role:remove', '#', 4, 0, 0),
(1015, 101, '0,1,101', '角色导出', 'F', '', NULL, 'system:role:export', '#', 5, 0, 0),
(1021, 102, '0,1,102', '菜单查询', 'F', '', NULL, 'system:menu:query', '#', 1, 0, 0),
(1022, 102, '0,1,102', '菜单新增', 'F', '', NULL, 'system:menu:add', '#', 2, 0, 0),
(1023, 102, '0,1,102', '菜单修改', 'F', '', NULL, 'system:menu:edit', '#', 3, 0, 0),
(1024, 102, '0,1,102', '菜单删除', 'F', '', NULL, 'system:menu:remove', '#', 4, 0, 0),
(1031, 103, '0,1,103', '部门查询', 'F', '', NULL, 'system:dept:query', '#', 1, 0, 0),
(1032, 103, '0,1,103', '部门新增', 'F', '', NULL, 'system:dept:add', '#', 2, 0, 0),
(1033, 103, '0,1,103', '部门修改', 'F', '', NULL, 'system:dept:edit', '#', 3, 0, 0),
(1034, 103, '0,1,103', '部门删除', 'F', '', NULL, 'system:dept:remove', '#', 4, 0, 0),
(1041, 104, '0,1,104', '岗位查询', 'F', '', NULL, 'system:post:query', '#', 1, 0, 0),
(1042, 104, '0,1,104', '岗位新增', 'F', '', NULL, 'system:post:add', '#', 2, 0, 0),
(1043, 104, '0,1,104', '岗位修改', 'F', '', NULL, 'system:post:edit', '#', 3, 0, 0),
(1044, 104, '0,1,104', '岗位删除', 'F', '', NULL, 'system:post:remove', '#', 4, 0, 0),
(1045, 104, '0,1,104', '岗位导出', 'F', '', NULL, 'system:post:export', '#', 5, 0, 0),
(1051, 105, '0,1,105', '字典查询', 'F', '', NULL, 'system:dict:query', '#', 1, 0, 0),
(1052, 105, '0,1,105', '字典新增', 'F', '', NULL, 'system:dict:add', '#', 2, 0, 0),
(1053, 105, '0,1,105', '字典修改', 'F', '', NULL, 'system:dict:edit', '#', 3, 0, 0),
(1054, 105, '0,1,105', '字典删除', 'F', '', NULL, 'system:dict:remove', '#', 4, 0, 0),
(1055, 105, '0,1,105', '字典导出', 'F', '', NULL, 'system:dict:export', '#', 5, 0, 0),
(1061, 106, '0,1,106', '参数查询', 'F', '', NULL, 'system:config:query', '#', 1, 0, 0),
(1062, 106, '0,1,106', '参数新增', 'F', '', NULL, 'system:config:add', '#', 2, 0, 0),
(1063, 106, '0,1,106', '参数修改', 'F', '', NULL, 'system:config:edit', '#', 3, 0, 0),
(1064, 106, '0,1,106', '参数删除', 'F', '', NULL, 'system:config:remove', '#', 4, 0, 0),
(1065, 106, '0,1,106', '参数导出', 'F', '', NULL, 'system:config:export', '#', 5, 0, 0),
(1071, 107, '0,1,107', '公告查询', 'F', '', NULL, 'system:notice:query', '#', 1, 0, 0),
(1072, 107, '0,1,107', '公告新增', 'F', '', NULL, 'system:notice:add', '#', 2, 0, 0),
(1073, 107, '0,1,107', '公告修改', 'F', '', NULL, 'system:notice:edit', '#', 3, 0, 0),
(1074, 107, '0,1,107', '公告删除', 'F', '', NULL, 'system:notice:remove', '#', 4, 0, 0);

-- 8. 用户角色关联
INSERT INTO sys_user_role (user_id, role_id) VALUES (1, 1), (2, 2);

-- 9. 用户岗位关联
INSERT INTO sys_user_post (user_id, post_id) VALUES (1, 1), (2, 4);

-- 10. 角色菜单关联(超管拥有全部菜单)
INSERT INTO sys_role_menu (role_id, menu_id) SELECT 1, id FROM sys_menu WHERE del_flag = 0;

-- 11. 字典类型
INSERT INTO sys_dict_type (id, dict_name, dict_type, status, remark) VALUES
(1, '用户性别', 'sys_user_sex', 0, '用户性别列表'),
(2, '系统开关', 'sys_normal_disable', 0, '系统开关列表'),
(3, '菜单状态', 'sys_show_hide', 0, '菜单状态列表'),
(4, '系统是否', 'sys_yes_no', 0, '系统是否列表'),
(5, '通知类型', 'sys_notice_type', 0, '通知类型列表'),
(6, '通知状态', 'sys_notice_status', 0, '通知状态列表'),
(7, '操作类型', 'sys_oper_type', 0, '操作类型列表'),
(8, '系统状态', 'sys_common_status', 0, '登录状态列表'),
(9, '客户端类型', 'sys_client_type', 0, '客户端类型列表'),
(10, '数据范围', 'sys_data_scope', 0, '数据范围列表'),
(11, '租户隔离类型', 'sys_isolation_type', 0, '租户隔离类型列表');

-- 12. 字典数据
INSERT INTO sys_dict_data (id, dict_type, dict_label, dict_value, dict_sort, css_class, list_class, is_default, status) VALUES
(1, 'sys_user_sex', '未知', '0', 1, '', '', 0, 0),
(2, 'sys_user_sex', '男', '1', 2, '', '', 0, 0),
(3, 'sys_user_sex', '女', '2', 3, '', '', 0, 0),
(4, 'sys_normal_disable', '正常', '0', 1, '', 'primary', 1, 0),
(5, 'sys_normal_disable', '停用', '1', 2, '', 'danger', 0, 0),
(6, 'sys_show_hide', '显示', '0', 1, '', 'primary', 1, 0),
(7, 'sys_show_hide', '隐藏', '1', 2, '', 'danger', 0, 0),
(8, 'sys_yes_no', '是', 'Y', 1, '', 'primary', 1, 0),
(9, 'sys_yes_no', '否', 'N', 2, '', 'danger', 0, 0),
(10, 'sys_notice_type', '通知', '1', 1, '', 'warning', 0, 0),
(11, 'sys_notice_type', '公告', '2', 2, '', 'success', 0, 0),
(12, 'sys_notice_status', '正常', '0', 1, '', 'primary', 1, 0),
(13, 'sys_notice_status', '关闭', '1', 2, '', 'danger', 0, 0),
(14, 'sys_oper_type', '其它', '0', 0, '', 'info', 0, 0),
(15, 'sys_oper_type', '新增', '1', 1, '', 'info', 0, 0),
(16, 'sys_oper_type', '修改', '2', 2, '', 'info', 0, 0),
(17, 'sys_oper_type', '删除', '3', 3, '', 'danger', 0, 0),
(18, 'sys_oper_type', '查询', '4', 4, '', 'primary', 0, 0),
(19, 'sys_oper_type', '导出', '5', 5, '', 'warning', 0, 0),
(20, 'sys_oper_type', '导入', '6', 6, '', 'warning', 0, 0),
(21, 'sys_oper_type', '授权', '7', 7, '', 'primary', 0, 0),
(22, 'sys_oper_type', '强退', '8', 8, '', 'danger', 0, 0),
(23, 'sys_oper_type', '清空', '9', 9, '', 'danger', 0, 0),
(24, 'sys_common_status', '成功', '0', 1, '', 'success', 0, 0),
(25, 'sys_common_status', '失败', '1', 2, '', 'danger', 0, 0),
(26, 'sys_client_type', 'PC端', 'pc', 1, '', 'primary', 1, 0),
(27, 'sys_client_type', 'App端', 'app', 2, '', 'success', 0, 0),
(28, 'sys_client_type', 'H5端', 'h5', 3, '', 'info', 0, 0),
(29, 'sys_client_type', '微信小程序', 'wechat_mp', 4, '', 'success', 0, 0),
(30, 'sys_client_type', '微信公众号', 'wechat_oa', 5, '', 'success', 0, 0),
(31, 'sys_client_type', '开放API', 'api', 6, '', 'warning', 0, 0),
(32, 'sys_data_scope', '全部数据', '1', 1, '', 'primary', 0, 0),
(33, 'sys_data_scope', '自定义数据', '2', 2, '', 'info', 0, 0),
(34, 'sys_data_scope', '本部门数据', '3', 3, '', 'info', 0, 0),
(35, 'sys_data_scope', '本部门及以下', '4', 4, '', 'info', 0, 0),
(36, 'sys_data_scope', '仅本人数据', '5', 5, '', 'info', 0, 0),
(37, 'sys_isolation_type', '逻辑隔离', 'logical', 1, '', 'primary', 1, 0),
(38, 'sys_isolation_type', '物理隔离', 'physical', 2, '', 'warning', 0, 0),
(39, 'sys_isolation_type', '混合隔离', 'hybrid', 3, '', 'info', 0, 0);

-- 13. 参数配置
INSERT INTO sys_config (id, config_name, config_key, config_value, config_type, remark) VALUES
(1, '主框架页-默认皮肤样式名称', 'sys.index.skinName', 'skin-blue', 'Y', '蓝色 skin-blue、绿色 skin-green、紫色 skin-purple、红色 skin-red、黄色 skin-yellow'),
(2, '用户管理-账号初始密码', 'sys.user.initPassword', '123456', 'Y', '初始化密码 123456'),
(3, '主框架页-侧边栏主题', 'sys.index.sideTheme', 'theme-dark', 'Y', '深色主题theme-dark，浅色主题theme-light'),
(4, '账号自助-验证码开关', 'sys.account.captchaEnabled', 'true', 'Y', '是否开启验证码功能（true开启，false关闭）'),
(5, '账号自助-是否开启用户注册功能', 'sys.account.registerUser', 'false', 'Y', '是否开启注册用户功能（true开启，false关闭）'),
(6, '用户登录-黑名单列表', 'sys.login.blackIPList', '', 'Y', '设置登录IP黑名单限制，多个匹配项以;分隔，支持匹配（*通配、网段）');

-- 14. 客户端配置
INSERT INTO sys_client (id, client_key, client_secret, client_type, token_expire, refresh_expire, max_online, kick_strategy, status, remark) VALUES
(1, 'pc_admin', 'han_pc_secret_2024', 'pc', 1800, 604800, 1, 'kick_old', 0, 'PC后台管理端'),
(2, 'app_client', 'han_app_secret_2024', 'app', 604800, 2592000, 3, 'kick_old', 0, 'App移动端'),
(3, 'h5_client', 'han_h5_secret_2024', 'h5', 86400, 604800, 0, 'kick_old', 0, 'H5移动端'),
(4, 'wechat_mp', 'han_mp_secret_2024', 'wechat_mp', 2592000, 7776000, 0, 'kick_old', 0, '微信小程序'),
(5, 'wechat_oa', 'han_oa_secret_2024', 'wechat_oa', 604800, 2592000, 0, 'kick_old', 0, '微信公众号'),
(6, 'open_api', 'han_api_secret_2024', 'api', 3600, 86400, 0, 'reject_new', 0, '开放API接口');

