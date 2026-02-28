package com.han.common.security.aspect;

import com.han.common.core.exception.ForbiddenException;
import com.han.common.core.exception.UnauthorizedException;
import com.han.common.security.annotation.RequiresRole;
import com.han.common.security.context.SecurityContextHolder;
import com.han.common.security.domain.LoginUser;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * 角色校验切面
 */
@Aspect
@Component
public class RoleAspect {

    @Around("@annotation(requiresRole)")
    public Object checkRole(ProceedingJoinPoint point, RequiresRole requiresRole) throws Throwable {
        LoginUser user = SecurityContextHolder.getLoginUser();

        if (user == null) {
            throw new UnauthorizedException("未登录或登录已过期");
        }

        // 超级管理员跳过
        if (user.isAdmin()) {
            return point.proceed();
        }

        String[] roles = requiresRole.value();
        RequiresRole.Logical logical = requiresRole.logical();

        boolean hasRole;
        if (logical == RequiresRole.Logical.AND) {
            hasRole = Arrays.stream(roles).allMatch(user::hasRole);
        } else {
            hasRole = Arrays.stream(roles).anyMatch(user::hasRole);
        }

        if (!hasRole) {
            throw new ForbiddenException("无角色权限: " + String.join(",", roles));
        }

        return point.proceed();
    }
}
