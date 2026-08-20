package com.han.system.sdfz.education.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 学期与教室管理端可写字段。
 *
 * <p>学期阶段 {@code lifecycleStatus} 不在此列：它由定时任务按日期推进，人工改了也会被覆盖。</p>
 */
public final class EducationCalendarForms {

    private EducationCalendarForms() {
    }

    public record Semester(
            Long id,
            @NotNull Long schoolId,
            @NotNull Long academicYearId,
            /** 为兼容旧调用方保留字段；创建时由服务按名称生成，编辑时忽略。 */
            @Size(max = 64) String semesterCode,
            @NotBlank @Size(max = 128) String semesterName,
            @NotNull LocalDate beginDate,
            @NotNull LocalDate endDate,
            @NotNull Integer currentFlag,
            @NotNull Integer status,
            @Size(max = 500) String remark) {
    }

    public record Room(
            Long id,
            @NotNull Long schoolId,
            @NotBlank @Size(max = 64) String roomCode,
            @NotBlank @Size(max = 128) String roomName,
            @Size(max = 32) String roomType,
            @NotNull Integer status,
            @Size(max = 500) String remark) {
    }
}
