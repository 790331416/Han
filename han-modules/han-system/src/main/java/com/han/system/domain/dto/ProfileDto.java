package com.han.system.domain.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 个人信息修改 DTO
 */
@Data
public class ProfileDto {

    /** 昵称 */
    @Size(max = 30, message = "昵称长度不能超过30个字符")
    private String nickname;

    /** 手机号 */
    @Size(max = 11, message = "手机号长度不能超过11个字符")
    private String phone;

    /** 邮箱 */
    @Email(message = "邮箱格式不正确")
    @Size(max = 50, message = "邮箱长度不能超过50个字符")
    private String email;

    /** 性别 (0男 1女 2未知) */
    private Integer sex;
}
