package com.han.system.sdfz.education.domain;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 学年升级必须由管理员先明确班级映射，再创建预览批次并二次确认。 */
public final class EducationPromotionForms {
    private EducationPromotionForms() {
    }

    public record ClassMapping(
            @NotNull Long sourceClassId,
            Long targetClassId,
            @NotBlank @Size(max = 16) String action) {
    }

    public record Preview(
            @NotNull Long schoolId,
            @NotNull Long sourceAcademicYearId,
            @NotNull Long targetAcademicYearId,
            @NotEmpty List<@Valid ClassMapping> mappings,
            @Size(max = 500) String remark) {
    }

    public record Confirm(@NotNull Long batchId) {
    }
}
