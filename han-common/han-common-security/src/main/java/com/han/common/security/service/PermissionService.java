package com.han.common.security.service;

import com.han.common.security.context.SecurityContextHolder;
import com.han.common.security.domain.LoginUser;
import org.springframework.stereotype.Service;

/**
 * 权限校验服务 — 供 @PreAuthorize("@ss.hasAuthority(...)") 使用
 */
@Service("ss")
public class PermissionService {

    /**
     * 判断当前请求是否已由网关解析出登录用户。
     */
    public boolean isLogin() {
        return SecurityContextHolder.getLoginUser() != null;
    }

    /**
     * 判断当前用户是否拥有指定权限
     */
    public boolean hasAuthority(String authority) {
        LoginUser user = SecurityContextHolder.getLoginUser();
        if (user == null) {
            return false;
        }
        if (user.isAdmin()) {
            return true;
        }
        return user.hasPermission(authority);
    }

    /**
     * 判断当前用户是否拥有指定角色
     */
    public boolean hasRole(String roleKey) {
        LoginUser user = SecurityContextHolder.getLoginUser();
        if (user == null) {
            return false;
        }
        return user.hasRole(roleKey);
    }

    /**
     * 判断当前用户是否拥有任一权限
     */
    public boolean hasAnyAuthority(String... authorities) {
        for (String authority : authorities) {
            if (hasAuthority(authority)) {
                return true;
            }
        }
        return false;
    }
}
