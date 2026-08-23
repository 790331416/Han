package com.han.system.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 平台统一品牌设置，仅允许超级管理员提交。 */
@Data
public class SystemBrandDto {

    @NotBlank(message = "系统全称不能为空")
    @Size(max = 64, message = "系统全称不能超过64个字符")
    private String fullName;

    @NotBlank(message = "系统简称不能为空")
    @Size(max = 32, message = "系统简称不能超过32个字符")
    private String shortName;

    @NotBlank(message = "请选择统一展示名称")
    @Pattern(regexp = "FULL_NAME|SHORT_NAME", message = "统一展示名称取值无效")
    private String displayMode;

    @Size(max = 128, message = "登录页副标题不能超过128个字符")
    private String loginSubtitle;

    /** 仅用于测试环境的厂商 HTTP 注册兼容开关；未提交时保持原值。 */
    private Boolean allowInsecureVendorRegistration;
}
