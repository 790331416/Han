package com.han.common.security.aspect;

import com.han.common.core.exception.ForbiddenException;
import com.han.common.core.exception.UnauthorizedException;
import com.han.common.security.annotation.RequiresRole;
import com.han.common.security.context.SecurityContextHolder;
import com.han.common.security.domain.LoginUser;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * 角色校验切面
 *
 * <p>与 {@link PermissionAspect} 同理：{@link RequiresRole} 允许标在类上，
 * 切点必须同时覆盖 {@code @annotation} 与 {@code @within}。
 */
@Aspect
@Component
public class RoleAspect {

    @Around("@annotation(com.han.common.security.annotation.RequiresRole) "
            + "|| @within(com.han.common.security.annotation.RequiresRole)")
    public Object checkRole(ProceedingJoinPoint point) throws Throwable {
        RequiresRole requiresRole = resolveRole(point);
        if (requiresRole == null) {
            return point.proceed();
        }

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

    /**
     * 方法级注解优先，其次取类级注解。
     */
    private RequiresRole resolveRole(ProceedingJoinPoint point) {
        Method method = ((MethodSignature) point.getSignature()).getMethod();
        RequiresRole requiresRole = AnnotatedElementUtils.findMergedAnnotation(method, RequiresRole.class);
        if (requiresRole != null) {
            return requiresRole;
        }
        Class<?> targetClass = point.getTarget() != null ? point.getTarget().getClass() : method.getDeclaringClass();
        return AnnotatedElementUtils.findMergedAnnotation(targetClass, RequiresRole.class);
    }
}
