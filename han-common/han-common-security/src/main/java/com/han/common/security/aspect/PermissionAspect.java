package com.han.common.security.aspect;

import com.han.common.core.exception.ForbiddenException;
import com.han.common.core.exception.UnauthorizedException;
import com.han.common.security.annotation.RequiresPermission;
import com.han.common.security.context.SecurityContextHolder;
import com.han.common.security.domain.LoginUser;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * 权限校验切面
 *
 * <p>{@link RequiresPermission} 的 {@code @Target} 允许标在类上，切点必须同时覆盖
 * {@code @annotation} 与 {@code @within}，否则类级注解会静默失效——看着有权限控制，实际没有。
 */
@Aspect
@Component
public class PermissionAspect {

    @Around("@annotation(com.han.common.security.annotation.RequiresPermission) "
            + "|| @within(com.han.common.security.annotation.RequiresPermission)")
    public Object checkPermission(ProceedingJoinPoint point) throws Throwable {
        RequiresPermission permission = resolvePermission(point);
        if (permission == null) {
            return point.proceed();
        }

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

    /**
     * 方法级注解优先，其次取类级注解。
     */
    private RequiresPermission resolvePermission(ProceedingJoinPoint point) {
        Method method = ((MethodSignature) point.getSignature()).getMethod();
        RequiresPermission permission = AnnotatedElementUtils.findMergedAnnotation(method, RequiresPermission.class);
        if (permission != null) {
            return permission;
        }
        Class<?> targetClass = point.getTarget() != null ? point.getTarget().getClass() : method.getDeclaringClass();
        return AnnotatedElementUtils.findMergedAnnotation(targetClass, RequiresPermission.class);
    }
}
