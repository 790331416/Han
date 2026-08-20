package com.han.system.sdfz.education.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 区域树写入模型；编码由服务端基于名称生成，避免前端自行造码。 */
public final class EducationRegionForms {
    private EducationRegionForms() {
    }

    public record Region(
            Long id,
            Long parentId,
            @NotBlank @Size(max = 128) String regionName,
            @NotBlank @Size(max = 32) String regionLevel,
            @NotNull Integer sort,
            @NotNull Integer status,
            @Size(max = 500) String remark) {
    }
}
