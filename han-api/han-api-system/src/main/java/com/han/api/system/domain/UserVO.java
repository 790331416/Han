package com.han.api.system.domain;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * 用户信息VO
 */
@Data
public class UserVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户ID */
    private Long userId;

    /** 租户ID */
    private Long tenantId;

    /** 部门ID */
    private Long deptId;

    /** 用户名 */
    private String username;

    /** 昵称 */
    private String nickname;

    /** 密码（加密后） */
    private String password;

    /** 头像 */
    private String avatar;

    /** 手机号 */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 性别 */
    private Integer sex;

    /** 状态 */
    private Integer status;

    /** 最后登录IP */
    private String loginIp;

    /** 最后登录时间 */
    private LocalDateTime loginTime;

    /** 角色ID列表 */
    private Set<Long> roleIds;

    /** 角色Key列表 */
    private Set<String> roleKeys;

    /** 权限列表 */
    private Set<String> permissions;

    /** 是否管理员 */
    public boolean isAdmin() {
        return userId != null && userId == 1L;
    }
}
