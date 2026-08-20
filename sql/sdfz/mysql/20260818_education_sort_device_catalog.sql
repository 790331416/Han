-- 巴蜀云校教育基础数据第二阶段：树排序、设备应用类型与设备字典。
-- 前置：在 han 逻辑库执行；本脚本只新增列和字典，不修改或删除历史业务数据。
SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS sdfz_education_add_column_if_absent;
DELIMITER $$
CREATE PROCEDURE sdfz_education_add_column_if_absent(
    IN p_table VARCHAR(64), IN p_column VARCHAR(64), IN p_definition TEXT)
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = p_table)
       AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                       WHERE table_schema = DATABASE() AND table_name = p_table AND column_name = p_column) THEN
        SET @education_ddl = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN `', p_column, '` ', p_definition);
        PREPARE education_stmt FROM @education_ddl;
        EXECUTE education_stmt;
        DEALLOCATE PREPARE education_stmt;
    END IF;
END$$
DELIMITER ;

CALL sdfz_education_add_column_if_absent('edu_class', 'sort', 'INT NOT NULL DEFAULT 0 COMMENT ''年级、专业和班级的显示排序''');
CALL sdfz_education_add_column_if_absent('edu_room', 'sort', 'INT NOT NULL DEFAULT 0 COMMENT ''建筑、楼层和场所的显示排序''');
CALL sdfz_education_add_column_if_absent('edu_device', 'application_types', 'VARCHAR(1024) NULL COMMENT ''设备应用类型字典编码，逗号分隔''');
DROP PROCEDURE IF EXISTS sdfz_education_add_column_if_absent;

INSERT INTO sys_dict_type (id, tenant_id, dict_name, dict_type, status, del_flag, remark)
SELECT source.id, NULL, source.dict_name, source.dict_type, 0, 0, '巴蜀云校设备管理'
FROM (
    SELECT 202608180101 AS id, '教育设备类型' AS dict_name, 'edu_device_type' AS dict_type
    UNION ALL SELECT 202608180102, '教育设备应用类型', 'edu_device_application'
    UNION ALL SELECT 202608180103, '教育资产状态', 'edu_asset_status'
) source
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_type item
    WHERE item.dict_type = source.dict_type AND item.tenant_id IS NULL AND item.del_flag = 0
);

INSERT INTO sys_dict_data (id, tenant_id, dict_type, dict_label, dict_value, dict_sort, is_default, status, del_flag, remark)
SELECT source.id, NULL, source.dict_type, source.dict_label, source.dict_value, source.dict_sort, 0, 0, 0, '巴蜀云校设备管理'
FROM (
    SELECT 202608180201 AS id, 'edu_device_type' AS dict_type, '视频分析' AS dict_label, 'VIDEO_ANALYSIS' AS dict_value, 10 AS dict_sort
    UNION ALL SELECT 202608180202, 'edu_device_type', '用电安全', 'ELECTRICAL_SAFETY', 20
    UNION ALL SELECT 202608180203, 'edu_device_type', '预警设备', 'WARNING_DEVICE', 30
    UNION ALL SELECT 202608180204, 'edu_device_type', '烟雾报警', 'SMOKE_ALARM', 40
    UNION ALL SELECT 202608180205, 'edu_device_type', '空气质量', 'AIR_QUALITY', 50
    UNION ALL SELECT 202608180206, 'edu_device_type', '考勤设备', 'ATTENDANCE_DEVICE', 60
    UNION ALL SELECT 202608180207, 'edu_device_type', '食品检测', 'FOOD_TESTING', 70
    UNION ALL SELECT 202608180208, 'edu_device_type', '其他', 'OTHER_DEVICE', 80
    UNION ALL SELECT 202608180209, 'edu_device_type', '电子班牌', 'ELECTRONIC_CLASS_BOARD', 90
    UNION ALL SELECT 202608180210, 'edu_device_type', '开放课堂', 'OPEN_CLASSROOM', 100
    UNION ALL SELECT 202608180211, 'edu_device_type', '智能设备', 'SMART_DEVICE', 110
    UNION ALL SELECT 202608180212, 'edu_device_type', '录播设备', 'RECORDER', 120
    UNION ALL SELECT 202608180221, 'edu_device_application', '校园监控', 'VIDEO_ANALYSIS:CAMPUS_MONITORING', 10
    UNION ALL SELECT 202608180222, 'edu_device_application', '智能巡课', 'VIDEO_ANALYSIS:INTELLIGENT_PATROL', 20
    UNION ALL SELECT 202608180223, 'edu_device_application', '阳光厨房', 'VIDEO_ANALYSIS:SUNSHINE_KITCHEN', 30
    UNION ALL SELECT 202608180224, 'edu_device_application', '阳光餐厅', 'VIDEO_ANALYSIS:SUNSHINE_CANTEEN', 40
    UNION ALL SELECT 202608180225, 'edu_device_application', '周界防范', 'VIDEO_ANALYSIS:PERIMETER_SECURITY', 50
    UNION ALL SELECT 202608180226, 'edu_device_application', '云上课堂-班级监控', 'VIDEO_ANALYSIS:CLOUD_CLASS_MONITORING', 60
    UNION ALL SELECT 202608180227, 'edu_device_application', '开放课堂-公共场所', 'VIDEO_ANALYSIS:OPEN_CLASS_PUBLIC', 70
    UNION ALL SELECT 202608180228, 'edu_device_application', '开放课堂-班级课堂', 'VIDEO_ANALYSIS:OPEN_CLASS_CLASS', 80
    UNION ALL SELECT 202608180229, 'edu_device_application', '用电安全', 'ELECTRICAL_SAFETY:ELECTRICAL_SAFETY', 10
    UNION ALL SELECT 202608180230, 'edu_device_application', '预警设备', 'WARNING_DEVICE:WARNING_DEVICE', 10
    UNION ALL SELECT 202608180231, 'edu_device_application', '烟雾报警', 'SMOKE_ALARM:SMOKE_ALARM', 10
    UNION ALL SELECT 202608180232, 'edu_device_application', '空气质量', 'AIR_QUALITY:AIR_QUALITY', 10
    UNION ALL SELECT 202608180233, 'edu_device_application', '宿舍', 'AIR_QUALITY:DORMITORY', 20
    UNION ALL SELECT 202608180234, 'edu_device_application', '会议室', 'AIR_QUALITY:MEETING_ROOM', 30
    UNION ALL SELECT 202608180235, 'edu_device_application', '食堂', 'AIR_QUALITY:CANTEEN', 40
    UNION ALL SELECT 202608180236, 'edu_device_application', '其他', 'AIR_QUALITY:OTHER', 50
    UNION ALL SELECT 202608180237, 'edu_device_application', '考勤', 'ATTENDANCE_DEVICE:ATTENDANCE', 10
    UNION ALL SELECT 202608180238, 'edu_device_application', '食品检测', 'FOOD_TESTING:FOOD_TESTING', 10
    UNION ALL SELECT 202608180239, 'edu_device_application', '其他', 'OTHER_DEVICE:OTHER', 10
    UNION ALL SELECT 202608180240, 'edu_device_application', '电子班牌', 'ELECTRONIC_CLASS_BOARD:ELECTRONIC_CLASS_BOARD', 10
    UNION ALL SELECT 202608180241, 'edu_device_application', '开放课堂', 'OPEN_CLASSROOM:OPEN_CLASSROOM', 10
    UNION ALL SELECT 202608180242, 'edu_device_application', '班级课堂', 'OPEN_CLASSROOM:CLASSROOM', 20
    UNION ALL SELECT 202608180243, 'edu_device_application', '云上课堂-互动盒子', 'SMART_DEVICE:CLOUD_CLASS_INTERACTION_BOX', 10
    UNION ALL SELECT 202608180244, 'edu_device_application', '直播', 'RECORDER:LIVE', 10
    UNION ALL SELECT 202608180245, 'edu_device_application', '录制', 'RECORDER:RECORD', 20
    UNION ALL SELECT 202608180251, 'edu_asset_status', '在用', 'IN_USE', 10
    UNION ALL SELECT 202608180252, 'edu_asset_status', '闲置', 'IDLE', 20
    UNION ALL SELECT 202608180253, 'edu_asset_status', '维修中', 'MAINTENANCE', 30
    UNION ALL SELECT 202608180254, 'edu_asset_status', '报废', 'SCRAPPED', 40
    UNION ALL SELECT 202608180255, 'edu_asset_status', '在用', 'IN_SERVICE', 50
) source
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_data item
    WHERE item.dict_type = source.dict_type AND item.dict_value = source.dict_value
      AND item.tenant_id IS NULL AND item.del_flag = 0
);
