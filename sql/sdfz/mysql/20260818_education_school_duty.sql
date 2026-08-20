-- 学校职务字典：与管理端 sys_role 分离，供人员编辑页和后端校验共同使用。
SET NAMES utf8mb4;

INSERT INTO sys_dict_type (id, tenant_id, dict_name, dict_type, status, remark, del_flag)
SELECT 202608180001, 1, '学校职务', 'edu_school_duty', 0, '校端学校职务；学生不配置职务', 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_type
    WHERE dict_type = 'edu_school_duty' AND tenant_id = 1 AND del_flag = 0
);

INSERT INTO sys_dict_data
    (id, tenant_id, dict_sort, dict_label, dict_value, dict_type, is_default, status, remark, del_flag)
SELECT 202608180101, 1, 1, '管理员', 'SCHOOL_ADMIN', 'edu_school_duty', 0, 0, '可进入校端控制台并创建、预约课程', 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_data
    WHERE dict_type = 'edu_school_duty' AND dict_value = 'SCHOOL_ADMIN' AND tenant_id = 1 AND del_flag = 0
);

INSERT INTO sys_dict_data
    (id, tenant_id, dict_sort, dict_label, dict_value, dict_type, is_default, status, remark, del_flag)
SELECT 202608180102, 1, 2, '普通教师', 'TEACHER', 'edu_school_duty', 1, 0, '教师默认学校职务', 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_data
    WHERE dict_type = 'edu_school_duty' AND dict_value = 'TEACHER' AND tenant_id = 1 AND del_flag = 0
);

-- 学生身份固定为 STUDENT，不保留历史教师/管理员职务。
UPDATE edu_person
SET duty_code = NULL
WHERE person_type = 'STUDENT';
