-- 删除初始化时带入、但当前产品没有后端和前端实现的菜单叶子。
-- 菜单表仍是前后端唯一来源；不在前端静默伪造页面。
SET NAMES utf8mb4;

DELETE FROM sys_role_menu
WHERE menu_id IN (
    SELECT id FROM sys_menu
    WHERE perms IN ('system:client:list', 'tool:swagger:list')
);
DELETE FROM sys_menu
WHERE perms IN ('system:client:list', 'tool:swagger:list');
