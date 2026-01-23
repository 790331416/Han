package com.xuman.auth.domain;

import com.xuman.common.core.enums.ClientType;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录请求DTO
 */
@Data
public class LoginDTO {

    /** 用户名 */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /** 密码 */
    @NotBlank(message = "密码不能为空")
    private String password;

    /** 验证码 */
    private String code;

    /** 验证码UUID */
    private String uuid;

    /** 客户端类型 */
    private ClientType clientType;

    /** 设备ID */
    private String deviceId;

    /** 租户ID */
    private Long tenantId;
}
