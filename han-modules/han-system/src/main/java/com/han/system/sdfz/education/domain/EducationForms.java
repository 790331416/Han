package com.han.system.sdfz.education.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * 教育主数据管理端可写字段。外部 ID、同步摘要和审计字段只允许同步服务维护。
 */
public final class EducationForms {

    private EducationForms() {
    }

    public record School(
            Long id,
            Long parentId,
            @Size(max = 64) String schoolCode,
            @NotBlank @Size(max = 128) String schoolName,
            @NotBlank @Size(max = 16) String schoolRole,
            @NotBlank @Size(max = 32) String areaCode,
            @NotNull Integer status,
            @Size(max = 500) String remark) {
    }

    public record ClassInfo(
            Long id,
            @NotNull Long schoolId,
            @Size(max = 32) String gradeCode,
            @Size(max = 64) String classCode,
            @NotBlank @Size(max = 128) String className,
            @NotBlank @Size(max = 16) String classRole,
            @NotNull Integer status,
            @Size(max = 500) String remark) {
    }

    /**
     * 人员统一入口。
     *
     * <p>不接受调用方指定 Han 用户 ID：启用登录时由服务端建号并回填，未启用登录时保持为空。</p>
     */
    public record Person(
            Long id,
            @NotNull Long schoolId,
            @Size(max = 64) String personNo,
            @NotBlank @Size(max = 128) String personName,
            @NotBlank @Size(max = 16) String personType,
            /**
             * 校内岗位（{@link EduDuty}）。缺省按普通教师处理，校级管理岗必须显式选择。
             * 与 personType 是两个维度：一个是身份类型，一个是职务。
             */
            @Size(max = 32) String dutyCode,
            @NotBlank @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确") String phone,
            @NotNull Integer status,
            @Size(max = 500) String remark,
            /** 0=在校 1=离校；缺省按在校处理。与账号停用独立。 */
            Integer leaveFlag,
            Boolean loginEnabled,
            @Size(max = 30) String username,
            @Size(max = 20) String password,
            List<Long> roleIds,
            Boolean clearRoles,
            List<Long> classIds,
            @Size(max = 32) String membershipRole,
            List<Long> subjectIds) {

        public boolean wantsLogin() {
            return Boolean.TRUE.equals(loginEnabled);
        }

        /**
         * 是否要改写角色集合。
         *
         * <p>空数组不再等于"清空角色"：调用方漏传或前端未回填时会是空数组，按清空处理会静默丢权限。
         * 要清空必须显式传 {@code clearRoles = true}。</p>
         */
        public boolean wantsRoleChange() {
            return Boolean.TRUE.equals(clearRoles) || (roleIds != null && !roleIds.isEmpty());
        }

        public List<Long> effectiveRoleIds() {
            return Boolean.TRUE.equals(clearRoles) ? List.of() : (roleIds == null ? List.of() : roleIds);
        }
    }

    /**
     * 人员统一写入结果。初始密码只在服务端生成时回传一次，不落库存明文、不进操作日志。
     */
    public record PersonResult(
            Long personId,
            Long userId,
            String username,
            String initialPassword) {
    }

    /** 批量导入逐行结果；不返回密码，避免初始口令进入批量响应。 */
    public record PersonImportResult(
            int rowNumber,
            String personName,
            String phone,
            boolean success,
            String message,
            Long personId,
            Long userId) {
    }

    public record Subject(
            Long id,
            @NotNull Long schoolId,
            /** 新增时由服务端按学校和科目名称生成；编辑时忽略调用方传值以保持编码稳定。 */
            @Size(max = 64) String subjectCode,
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
            @NotEmpty List<@NotBlank @Size(max = 128) String> applicationTypes,
            @Size(max = 128) String model,
            @Size(max = 128) String serialNumber,
            @NotBlank @Size(max = 32) String assetStatus,
            @NotNull Integer status,
            @Size(max = 500) String remark) {
    }

    // 学期与教室的表单在 EducationCalendarForms：那一版是端点的唯一归属方。

    /** 归班关系：一次提交某人员在某学校下的全部有效班级，服务端按集合收敛。 */
    public record Membership(
            @NotNull Long personId,
            @NotNull List<Long> classIds,
            @Size(max = 32) String membershipRole) {
    }

    /** 任教关系：一次提交某人员的全部有效任教科目，服务端按集合收敛。 */
    public record TeachingAssignment(
            @NotNull Long personId,
            @NotNull List<Long> subjectIds,
            Long classId) {
    }

    /** 逻辑删除请求。 */
    public record DeleteRequest(
            @NotEmpty List<Long> ids) {
    }
}
