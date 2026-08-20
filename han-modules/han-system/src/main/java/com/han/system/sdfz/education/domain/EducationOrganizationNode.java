package com.han.system.sdfz.education.domain;

import java.util.ArrayList;
import java.util.List;

/** 管理端组织树展示节点；不向前端暴露同步摘要等内部字段。 */
public record EducationOrganizationNode(
        Long id,
        Long parentId,
        String schoolCode,
        String schoolName,
        String orgType,
        String schoolManageType,
        String schoolProperty,
        Long regionId,
        String regionCode,
        String regionName,
        Integer nodeLevel,
        Integer autoUpgradeEnabled,
        Integer status,
        List<EducationOrganizationNode> children) {

    public static EducationOrganizationNode from(EduSchoolPo school, String regionName) {
        return new EducationOrganizationNode(
                school.getId(), school.getParentId(), school.getSchoolCode(), school.getSchoolName(),
                school.getOrgType(), school.getSchoolManageType(), school.getSchoolProperty(), school.getRegionId(),
                school.getAreaCode(), regionName, school.getNodeLevel(), school.getAutoUpgradeEnabled(), school.getStatus(),
                new ArrayList<>());
    }
}
