package com.han.common.security.context;

import com.han.common.core.context.SecurityContext;
import com.han.common.security.domain.LoginUser;
import org.springframework.stereotype.Component;

/**
 * SecurityContext 接口的实现
 * <p>
 * 委托给 SecurityContextHolder（TransmittableThreadLocal）获取当前登录用户信息。
 */
@Component
public class SecurityContextAdapter implements SecurityContext {

    @Override
    public Long getUserId() {
        return SecurityContextHolder.getUserId();
    }

    @Override
    public Long getTenantId() {
        return SecurityContextHolder.getTenantId();
    }

    @Override
    public Long getDeptId() {
        return SecurityContextHolder.getDeptId();
    }

    @Override
    public String getNickname() {
        LoginUser user = SecurityContextHolder.getLoginUser();
        return user != null ? user.getNickname() : null;
    }

    @Override
    public boolean isLogin() {
        return SecurityContextHolder.isLogin();
    }

    @Override
    public boolean isAdmin() {
        return SecurityContextHolder.isAdmin();
    }
}
