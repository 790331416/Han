package com.han.common.mybatis.aspect;

import com.baomidou.mybatisplus.core.plugins.IgnoreStrategy;
import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import com.han.common.tenant.annotation.IgnoreTenant;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * @IgnoreTenant 注解 AOP 处理器
 * <p>
 * 拦截标注了 @IgnoreTenant 的方法或类，在执行期间自动跳过租户过滤。
 */
@Slf4j
@Aspect
@Component
public class IgnoreTenantAspect {

    @Around("@annotation(ignoreTenant)")
    public Object aroundMethod(ProceedingJoinPoint joinPoint, IgnoreTenant ignoreTenant) throws Throwable {
        return executeIgnoringTenant(joinPoint);
    }

    @Around("@within(ignoreTenant) && !@annotation(com.han.common.tenant.annotation.IgnoreTenant)")
    public Object aroundClass(ProceedingJoinPoint joinPoint, IgnoreTenant ignoreTenant) throws Throwable {
        return executeIgnoringTenant(joinPoint);
    }

    private Object executeIgnoringTenant(ProceedingJoinPoint joinPoint) throws Throwable {
        InterceptorIgnoreHelper.handle(IgnoreStrategy.builder().tenantLine(true).build());
        try {
            return joinPoint.proceed();
        } finally {
            InterceptorIgnoreHelper.clearIgnoreStrategy();
        }
    }
}
