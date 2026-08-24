-- 教育数据权限收口：区域只作为组织属性，人员范围只授权教育局或学校。
SET NAMES utf8mb4;

-- 根教育局按省级名称补区域；下级组织继承上级区域作为最低安全基线。
UPDATE edu_school organization
JOIN edu_region region
  ON region.tenant_id = organization.tenant_id
 AND region.region_level = 'PROVINCE'
 AND organization.school_name LIKE CONCAT(region.region_name, '%')
SET organization.region_id = region.id,
    organization.area_code = region.region_code
WHERE organization.del_flag = 0
  AND organization.parent_id IS NULL
  AND organization.region_id IS NULL;

UPDATE edu_school child
JOIN edu_school parent ON parent.id = child.parent_id AND parent.tenant_id = child.tenant_id
SET child.region_id = parent.region_id,
    child.area_code = parent.area_code
WHERE child.del_flag = 0 AND child.region_id IS NULL AND parent.region_id IS NOT NULL;

-- 已有下级组织若落在上级区域之外，以组织隶属关系为准收敛到上级区域。
UPDATE edu_school child
JOIN edu_school parent ON parent.id = child.parent_id AND parent.tenant_id = child.tenant_id
JOIN edu_region child_region ON child_region.id = child.region_id AND child_region.tenant_id = child.tenant_id
SET child.region_id = parent.region_id,
    child.area_code = parent.area_code
WHERE child.del_flag = 0
  AND parent.del_flag = 0
  AND parent.region_id IS NOT NULL
  AND child.region_id <> parent.region_id
  AND NOT FIND_IN_SET(parent.region_id, child_region.ancestors);

UPDATE edu_school child
JOIN edu_school parent ON parent.id = child.parent_id AND parent.tenant_id = child.tenant_id
SET child.region_id = parent.region_id,
    child.area_code = parent.area_code
WHERE child.del_flag = 0 AND child.region_id IS NULL AND parent.region_id IS NOT NULL;

-- 存量区域授权转换为该区域唯一直属教育组织授权；线上现有甘肃省授权即转换为甘肃省教育局。
UPDATE edu_user_scope scope
JOIN edu_school organization
  ON organization.tenant_id = scope.tenant_id
 AND organization.region_id = scope.scope_id
 AND organization.del_flag = 0
SET scope.scope_type = 'ORG',
    scope.scope_id = organization.id,
    scope.remark = COALESCE(scope.remark, '由区域授权迁移为教育组织授权')
WHERE scope.del_flag = 0
  AND scope.status = 0
  AND scope.scope_type = 'REGION'
  AND (SELECT COUNT(*) FROM edu_school candidate
       WHERE candidate.tenant_id = scope.tenant_id
         AND candidate.region_id = scope.scope_id
         AND candidate.del_flag = 0) = 1;

-- 当前生产账号只有 DML 权限，硬绑定由管理端和数字校园同步入口共同校验；
-- 发布脚本必须在切换应用前确认下面两个计数都为 0。
SELECT COUNT(*) AS missing_region_count
FROM edu_school WHERE del_flag = 0 AND region_id IS NULL;

SELECT COUNT(*) AS non_org_scope_count
FROM edu_user_scope WHERE del_flag = 0 AND status = 0 AND scope_type <> 'ORG';

SELECT COUNT(*) AS invalid_org_region_count
FROM edu_school child
JOIN edu_school parent ON parent.id = child.parent_id AND parent.tenant_id = child.tenant_id
JOIN edu_region child_region ON child_region.id = child.region_id AND child_region.tenant_id = child.tenant_id
WHERE child.del_flag = 0
  AND parent.del_flag = 0
  AND child.region_id <> parent.region_id
  AND NOT FIND_IN_SET(parent.region_id, child_region.ancestors);
