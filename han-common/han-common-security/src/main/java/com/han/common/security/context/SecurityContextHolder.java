package com.han.common.security.context;

import com.alibaba.ttl.TransmittableThreadLocal;
import com.han.common.security.domain.LoginUser;

/**
 * 安全上下文持有者
 */
public class SecurityContextHolder {

    private static final TransmittableThreadLocal<LoginUser> LOGIN_USER = new TransmittableThreadLocal<>();

    private SecurityContextHolder() {}

    /**
     * 获取当前登录用户
     */
    public static LoginUser getLoginUser() {
        return LOGIN_USER.get();
    }

    /**
     * 设置当前登录用户
     */
    public static void setLoginUser(LoginUser loginUser) {
        LOGIN_USER.set(loginUser);
    }

    /**
     * 清除当前登录用户
     */
    public static void clear() {
        LOGIN_USER.remove();
    }

    /**
     * 获取用户ID
     */
    public static Long getUserId() {
        LoginUser user = getLoginUser();
        return user != null ? user.getUserId() : null;
    }

    /**
     * 获取租户ID
     */
    public static Long getTenantId() {
        LoginUser user = getLoginUser();
        return user != null ? user.getTenantId() : null;
    }

    /**
     * 获取部门ID
     */
    public static Long getDeptId() {
        LoginUser user = getLoginUser();
        return user != null ? user.getDeptId() : null;
    }

    /**
     * 获取用户名
     */
    public static String getUsername() {
        LoginUser user = getLoginUser();
        return user != null ? user.getUsername() : null;
    }

    /**
     * 是否已登录
     */
    public static boolean isLogin() {
        return getLoginUser() != null;
    }

    /**
     * 是否为管理员
     */
    public static boolean isAdmin() {
        LoginUser user = getLoginUser();
        return user != null && user.isAdmin();
    }
}
