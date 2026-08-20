package com.han.system.sdfz.education.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

import java.util.List;

/** 管理端课表节次表单。当前统一模板固定使用 templateId=1。 */
public final class EducationCourseRuleForms {

    private EducationCourseRuleForms() {
    }

    public record Rule(
            String id,
            @NotBlank(message = "模板名称不能为空") String templateName,
            @NotBlank(message = "开始时间不能为空")
            @Pattern(regexp = "^\\d{1,2}:\\d{2}(:\\d{2})?$", message = "开始时间格式应为 HH:mm") String startTime,
            @NotBlank(message = "结束时间不能为空")
            @Pattern(regexp = "^\\d{1,2}:\\d{2}(:\\d{2})?$", message = "结束时间格式应为 HH:mm") String endTime,
            @NotBlank(message = "节次不能为空")
            @Pattern(regexp = "^[1-9]\\d*$", message = "节次必须是正整数") String classSection) {
    }

    public record Status(String id, @NotBlank(message = "状态不能为空") @Pattern(regexp = "^[01]$") String status) {
    }

    public record DeleteRequest(@NotEmpty(message = "请选择要删除的节次") List<String> ids) {
    }
}
