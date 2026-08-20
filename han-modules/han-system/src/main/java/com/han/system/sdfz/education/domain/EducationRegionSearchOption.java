package com.han.system.sdfz.education.domain;

/** 区域选择器搜索结果；pathLabel 用于在不加载全国树的前提下展示完整归属路径。 */
public record EducationRegionSearchOption(
        Long id,
        Long parentId,
        String regionCode,
        String regionName,
        String regionLevel,
        Integer nodeLevel,
        String pathLabel) {
}
