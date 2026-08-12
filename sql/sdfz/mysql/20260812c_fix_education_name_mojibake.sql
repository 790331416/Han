-- 修复教育菜单与角色名称的双重编码乱码（MySQL 8.4）
--
-- 成因：20260811 / 20260812 两个脚本原本缺少 `SET NAMES utf8mb4;` 文件头。
-- 用 mysql 客户端手工执行时，会话字符集不是 utf8mb4，脚本里的 UTF-8 字节被当作 latin1
-- 再按 utf8mb4 编码入库，形成双重编码。「教育管理」4 个汉字变成 12 个字符。
-- 走 docker entrypoint 的 small-init-mysql.sql 和走应用 JDBC 的写入都不受影响，已核对为干净。
--
-- 还原：把双重编码反向解一次——按 latin1 取回原始字节，再按 utf8mb4 解释。
-- 可重复执行：只处理"当前不含汉字、还原后含汉字"的行，已经正确的行不会被再次转换。
-- 回滚：还原后的值即为脚本中的字面量，如需回退可重新执行 20260811 / 20260812 的对应 INSERT。

SET NAMES utf8mb4;

UPDATE sys_menu
   SET menu_name = CONVERT(BINARY(CONVERT(menu_name USING latin1)) USING utf8mb4)
 WHERE perms LIKE 'education:%'
   AND menu_name NOT REGEXP '[一-龥]'
   AND CONVERT(BINARY(CONVERT(menu_name USING latin1)) USING utf8mb4) REGEXP '[一-龥]';

UPDATE sys_role
   SET role_name = CONVERT(BINARY(CONVERT(role_name USING latin1)) USING utf8mb4)
 WHERE role_key IN ('teacher', 'student')
   AND role_name NOT REGEXP '[一-龥]'
   AND CONVERT(BINARY(CONVERT(role_name USING latin1)) USING utf8mb4) REGEXP '[一-龥]';

UPDATE sys_role
   SET remark = CONVERT(BINARY(CONVERT(remark USING latin1)) USING utf8mb4)
 WHERE role_key IN ('teacher', 'student')
   AND remark IS NOT NULL
   AND remark NOT REGEXP '[一-龥]'
   AND CONVERT(BINARY(CONVERT(remark USING latin1)) USING utf8mb4) REGEXP '[一-龥]';
