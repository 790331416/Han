package com.han.system.sdfz.education.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 教育局、学校、校区统一组织树的管理端写入模型。 */
public final class EducationOrganizationForms {

    private EducationOrganizationForms() {
    }

    public record Organization(
            Long id,
            Long parentId,
            @NotBlank @Size(max = 128) String schoolName,
            @NotBlank @Size(max = 32) String orgType,
            @Size(max = 32) String schoolManageType,
            @Size(max = 32) String schoolProperty,
            @NotNull Long regionId,
            @NotNull Integer autoUpgradeEnabled,
            @NotNull Integer status,
            @Size(max = 500) String remark) {
    }
}
