package com.xuman.system.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Set;

/**
 * 用户DTO
 */
@Data
public class UserDTO {

    /** 用户ID */
    private Long userId;

    /** 部门ID */
    private Long deptId;

    /** 用户名 */
    @NotBlank(message = "用户名不能为空")
    @Size(min = 2, max = 30, message = "用户名长度为2-30个字符")
    private String username;

    /** 昵称 */
    @Size(max = 30, message = "昵称长度不能超过30个字符")
    private String nickname;

    /** 密码 */
    private String password;

    /** 手机号 */
    @Size(max = 20, message = "手机号长度不能超过20个字符")
    private String phone;

    /** 邮箱 */
    @Size(max = 50, message = "邮箱长度不能超过50个字符")
    private String email;

    /** 性别 */
    private Integer sex;

    /** 状态 */
    private Integer status;

    /** 角色ID列表 */
    private Set<Long> roleIds;

    /** 岗位ID列表 */
    private Set<Long> postIds;

    /** 备注 */
    private String remark;
}
