package com.han.system.sdfz.education.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 年级、专业、班级树的管理端写入模型。 */
public final class EducationClassTreeForms {
    private EducationClassTreeForms() {
    }

    public record Node(
            Long id,
            @NotNull Long schoolId,
            Long parentId,
            @NotNull Long academicYearId,
            @NotBlank @Size(max = 128) String className,
            @NotBlank @Size(max = 16) String nodeType,
            @Size(max = 32) String branchCode,
            Integer cohortYear,
            @Size(max = 16) String classRole,
            @NotNull @Min(0) Integer sort,
            @NotNull Integer status,
            @Size(max = 500) String remark) {
    }

    /** 数字范围批量创建年级或班级；名称和排序值由服务端生成。 */
    public record Range(
            @NotNull Long schoolId,
            @NotNull Long academicYearId,
            Long parentId,
            @NotBlank @Size(max = 16) String nodeType,
            Integer cohortYear,
            @NotNull @Min(1) Integer startNo,
            @NotNull @Min(1) Integer endNo,
            @NotNull Integer status) {
    }
}
