-- 全国区域按父节点懒加载的查询索引（MySQL 8.0+）
--
-- 前置：已执行 20260818_education_school_scope_and_region.sql 并完成全国区域基准导入。
-- 目的：区域页与学校区域级联不再全表扫描、filesort 后再把四级树整棵返回浏览器。
-- 回滚：DROP INDEX idx_edu_region_tree_children ON edu_region;
--       DROP INDEX idx_edu_region_tree_order ON edu_region;
SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS sdfz_edu_add_region_index_if_absent;
DELIMITER $$
CREATE PROCEDURE sdfz_edu_add_region_index_if_absent(IN p_index VARCHAR(64), IN p_definition TEXT)
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'edu_region')
       AND NOT EXISTS (SELECT 1 FROM information_schema.statistics
                       WHERE table_schema = DATABASE() AND table_name = 'edu_region' AND index_name = p_index) THEN
        SET @ddl = CONCAT('ALTER TABLE `edu_region` ADD ', p_definition);
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

-- 懒加载：tenant + 父区域 + 状态限定后按展示顺序读取子节点。
CALL sdfz_edu_add_region_index_if_absent('idx_edu_region_tree_children',
    'KEY `idx_edu_region_tree_children` (`tenant_id`, `parent_id`, `status`, `del_flag`, `sort`, `region_name`)');

-- 兼容仍需全树的授权页，避免 node_level / sort / name 的 filesort。
CALL sdfz_edu_add_region_index_if_absent('idx_edu_region_tree_order',
    'KEY `idx_edu_region_tree_order` (`tenant_id`, `status`, `del_flag`, `node_level`, `sort`, `region_name`)');

DROP PROCEDURE IF EXISTS sdfz_edu_add_region_index_if_absent;
