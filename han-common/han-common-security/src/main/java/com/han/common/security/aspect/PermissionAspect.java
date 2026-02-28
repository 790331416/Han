package com.han.common.security.aspect;

import com.han.common.core.exception.ForbiddenException;
import com.han.common.core.exception.UnauthorizedException;
import com.han.common.security.annotation.RequiresPermission;
import com.han.common.security.context.SecurityContextHolder;
import com.han.common.security.domain.LoginUser;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * 权限校验切面
 */
@Aspect
@Component
public class PermissionAspect {

    @Around("@annotation(permission)")
    public Object checkPermission(ProceedingJoinPoint point, RequiresPermission permission) throws Throwable {
        LoginUser user = SecurityContextHolder.getLoginUser();

        if (user == null) {
            throw new UnauthorizedException("未登录或登录已过期");
        }

        // 超级管理员跳过权限校验
        if (user.isAdmin()) {
            return point.proceed();
        }

        String perm = permission.value();
        if (!user.hasPermission(perm)) {
            throw new ForbiddenException("无权限访问: " + perm);
        }

        return point.proceed();
    }
}
