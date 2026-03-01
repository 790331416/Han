package com.han.system.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * 用户数据传输对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SysUserDto {

    /** 用户ID */
    private Long userId;

    /** 部门ID */
    private Long deptId;

    /** 用户名 */
    @NotBlank(message = "用户名不能为空")
    @Size(min = 2, max = 30, message = "用户名长度为2-30个字符")
    private String username;

    /** 昵称 */
    @NotBlank(message = "昵称不能为空")
    @Size(max = 30, message = "昵称长度不能超过30个字符")
    private String nickname;

    /** 密码 */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    /** 头像 */
    private String avatar;

    /** 手机号 */
    @Size(max = 11, message = "手机号长度不能超过11个字符")
    private String phone;

    /** 邮箱 */
    @Email(message = "邮箱格式不正确")
    @Size(max = 50, message = "邮箱长度不能超过50个字符")
    private String email;

    /** 性别（0未知 1男 2女） */
    private Integer sex;

    /** 状态（0正常 1停用） */
    private Integer status;

    /** 备注 */
    private String remark;

    /** 角色ID列表 */
    private Set<Long> roleIds;

    /** 岗位ID列表 */
    private Set<Long> postIds;
}
