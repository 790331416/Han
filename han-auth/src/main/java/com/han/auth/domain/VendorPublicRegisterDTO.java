package com.han.auth.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

/** 厂商公开注册请求；默认使用 auth 注册公钥加密，测试兼容模式才允许明文。 */
@Data
public class VendorPublicRegisterDTO {

    @NotBlank(message = "厂商名称不能为空")
    @Size(max = 100, message = "厂商名称长度不能超过100个字符")
    private String name;

    @NotBlank(message = "统一社会信用代码不能为空")
    @Size(min = 15, max = 18, message = "统一社会信用代码长度应为15-18个字符")
    @Pattern(regexp = "[0-9A-Za-z]{15,18}", message = "统一社会信用代码格式不正确")
    private String qualificationNo;

    @NotBlank(message = "联系人姓名不能为空")
    @Size(max = 50, message = "联系人姓名长度不能超过50个字符")
    private String contactName;

    @NotBlank(message = "联系电话不能为空")
    @Size(max = 20, message = "联系电话长度不能超过20个字符")
    @Pattern(regexp = "[0-9+()\\- ]{6,20}", message = "联系电话格式不正确")
    private String contactPhone;

    /** 门户账号手机号；可与企业联系人电话不同。 */
    @NotBlank(message = "账号手机号不能为空")
    @Size(max = 20, message = "账号手机号长度不能超过20个字符")
    @Pattern(regexp = "[0-9+()\\- ]{6,20}", message = "账号手机号格式不正确")
    private String phone;

    @Email(message = "联系邮箱格式不正确")
    @Size(max = 100, message = "联系邮箱长度不能超过100个字符")
    private String contactEmail;

    @Email(message = "账号邮箱格式不正确")
    @Size(max = 100, message = "账号邮箱长度不能超过100个字符")
    private String email;
    @Size(max = 50, message = "所属行业长度不能超过50个字符")
    private String industry;
    @Size(max = 255, message = "官网地址长度不能超过255个字符")
    private String website;
    @Size(max = 500, message = "申请说明长度不能超过500个字符")
    private String applyReason;

    @NotBlank(message = "登录用户名不能为空")
    @Size(min = 2, max = 30, message = "用户名长度为2-30个字符")
    @Pattern(regexp = "[A-Za-z0-9_.-]{2,30}", message = "用户名仅支持字母、数字、下划线、点和短横线")
    private String username;

    @Size(max = 30, message = "昵称长度不能超过30个字符")
    private String nickname;

    /** RSA 密文；正式环境必须提供。 */
    @Size(max = 4096, message = "密码密文长度不能超过4096个字符")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @ToString.Exclude
    private String encryptedPassword;

    /** 仅在系统设置显式开启 HTTP 测试兼容时接收，绝不回传或记录。 */
    @Size(min = 8, max = 4096, message = "登录密码长度应为8-4096个字符")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @ToString.Exclude
    private String plainPassword;

    @Size(max = 20, message = "验证码长度不能超过20个字符")
    @Pattern(regexp = "[A-Za-z0-9]*", message = "验证码格式不正确")
    private String captchaCode;
    @Size(max = 64, message = "验证码标识长度不能超过64个字符")
    @Pattern(regexp = "[A-Za-z0-9-]*", message = "验证码标识格式不正确")
    private String captchaUuid;
}
