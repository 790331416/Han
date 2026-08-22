package com.han.api.system.domain;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

/**
 * 开放平台厂商门户账号创建请求。
 *
 * <p>只允许由开放平台内部调用方使用，系统服务负责校验密码、去重、落库和角色绑定。</p>
 */
@Data
public class OpenVendorAccountCreateDTO {

    /** 固定为平台租户。 */
    @NotNull(message = "租户ID不能为空")
    private Long tenantId;

    /** 登录用户名。 */
    @NotBlank(message = "用户名不能为空")
    @Size(min = 2, max = 30, message = "用户名长度为2-30个字符")
    @Pattern(regexp = "[A-Za-z0-9_.-]{2,30}", message = "用户名格式不正确")
    private String username;

    /** 显示昵称。 */
    @Size(max = 30, message = "昵称长度不能超过30个字符")
    private String nickname;

    /** 明文密码仅在内部请求生命周期内存在，系统服务不会回传或记录。 */
    @NotBlank(message = "密码不能为空")
    @Size(max = 4096, message = "密码长度不能超过4096个字符")
    @ToString.Exclude
    private String password;

    /** 厂商联系人手机号。 */
    @NotBlank(message = "手机号不能为空")
    @Size(max = 20, message = "手机号长度不能超过20个字符")
    @Pattern(regexp = "[0-9+()\\- ]{6,20}", message = "手机号格式不正确")
    private String phone;

    /** 门户账号邮箱。 */
    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱长度不能超过100个字符")
    private String email;
}
