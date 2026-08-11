package com.han.system.sdfz.education.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 教育主数据管理端可写字段。外部 ID、同步摘要和审计字段只允许同步服务维护。
 */
public final class EducationForms {

    private EducationForms() {
    }

    public record School(
            Long id,
            Long parentId,
            @NotBlank @Size(max = 64) String schoolCode,
            @NotBlank @Size(max = 128) String schoolName,
            @NotBlank @Size(max = 16) String schoolRole,
            @Size(max = 32) String areaCode,
            @NotNull Integer status,
            @Size(max = 500) String remark) {
    }

    public record ClassInfo(
            Long id,
            @NotNull Long schoolId,
            @Size(max = 32) String gradeCode,
            @NotBlank @Size(max = 64) String classCode,
            @NotBlank @Size(max = 128) String className,
            @NotBlank @Size(max = 16) String classRole,
            @NotNull Integer status,
            @Size(max = 500) String remark) {
    }

    public record Person(
            Long id,
            Long userId,
            @NotNull Long schoolId,
            @NotBlank @Size(max = 64) String personNo,
            @NotBlank @Size(max = 128) String personName,
            @NotBlank @Size(max = 16) String personType,
            @Size(max = 20) String phone,
            @NotNull Integer status,
            @Size(max = 500) String remark) {
    }

    public record Subject(
            Long id,
            @NotBlank @Size(max = 64) String subjectCode,
            @NotBlank @Size(max = 128) String subjectName,
            @NotNull Integer sort,
            @NotNull Integer status,
            @Size(max = 500) String remark) {
    }

    public record Device(
            Long id,
            @NotNull Long schoolId,
            Long roomId,
            @NotBlank @Size(max = 128) String deviceCode,
            @NotBlank @Size(max = 128) String deviceName,
            @NotBlank @Size(max = 64) String deviceType,
            @Size(max = 128) String model,
            @Size(max = 128) String serialNumber,
            @NotBlank @Size(max = 32) String assetStatus,
            @NotNull Integer status,
            @Size(max = 500) String remark) {
    }
}
