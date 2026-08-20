package com.han.system.sdfz.education.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/** 学年管理端写入模型。 */
public final class EducationAcademicYearForms {

    private EducationAcademicYearForms() {
    }

    public record AcademicYear(
            Long id,
            @NotNull Long schoolId,
            @NotBlank @Pattern(regexp = "^\\d{4}-\\d{4}$", message = "学年编码格式应为 YYYY-YYYY") String yearCode,
            @NotBlank @Size(max = 64) String yearName,
            @NotNull LocalDate beginDate,
            @NotNull LocalDate endDate,
            @NotBlank @Size(max = 16) String status,
            @Size(max = 500) String remark) {
    }

    public record DeleteRequest(@NotNull @Size(min = 1) List<Long> ids) {
    }
}
