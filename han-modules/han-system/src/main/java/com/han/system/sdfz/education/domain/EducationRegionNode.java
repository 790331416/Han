package com.han.system.sdfz.education.domain;

import java.util.ArrayList;
import java.util.List;

/** 区域树展示节点。 */
public record EducationRegionNode(
        Long id,
        Long parentId,
        String regionCode,
        String regionName,
        String regionLevel,
        String sourceSystem,
        Integer nodeLevel,
        Integer sort,
        Integer status,
        List<EducationRegionNode> children) {

    public static EducationRegionNode from(EduRegionPo region) {
        return new EducationRegionNode(region.getId(), region.getParentId(), region.getRegionCode(), region.getRegionName(),
                region.getRegionLevel(), region.getSourceSystem(), region.getNodeLevel(), region.getSort(), region.getStatus(), new ArrayList<>());
    }
}
