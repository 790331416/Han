package com.han.common.security.domain;

import com.han.common.core.enums.ClientType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.Set;

/**
 * 登录用户信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginUser implements Serializable {

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

    /** 密码（脱敏，不返回前端） */
    private transient String password;

    /** 头像 */
    private String avatar;

    /** 手机号 */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 客户端类型 */
    private ClientType clientType;

    /** 设备ID */
    private String deviceId;

    /** 登录IP */
    private String loginIp;

    /** 登录时间 */
    private Long loginTime;

    /** Token过期时间 */
    private Long expireTime;

    /** 角色ID列表 */
    private Set<Long> roleIds;

    /** 角色Key列表 */
    private Set<String> roleKeys;

    /** 权限列表 */
    private Set<String> permissions;

    /** 数据权限部门ID列表 */
    private Set<Long> deptIds;

    /**
     * 是否为超级管理员
     */
    public boolean isAdmin() {
        return userId != null && userId == 1L;
    }

    /**
     * 是否拥有指定权限
     */
    public boolean hasPermission(String permission) {
        if (isAdmin()) {
            return true;
        }
        return permissions != null && permissions.contains(permission);
    }

    /**
     * 是否拥有指定角色
     */
    public boolean hasRole(String roleKey) {
        if (isAdmin()) {
            return true;
        }
        return roleKeys != null && roleKeys.contains(roleKey);
    }
}
