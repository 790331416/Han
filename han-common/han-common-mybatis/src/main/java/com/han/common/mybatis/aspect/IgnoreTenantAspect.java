package com.han.common.mybatis.aspect;

import com.han.common.mybatis.helper.TenantHelper;
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
 * <p>
 * 每次生效都会打一条 debug 日志（含中文理由），便于在灰度期核对忽略点清单是否与
 * 实际放行路径一致。
 * <p>
 * 忽略作用域的开关委托给 {@link TenantHelper}，两套忽略机制共用一份重入计数。
 */
@Slf4j
@Aspect
@Component
public class IgnoreTenantAspect {

    @Around("@annotation(ignoreTenant)")
    public Object aroundMethod(ProceedingJoinPoint joinPoint, IgnoreTenant ignoreTenant) throws Throwable {
        return executeIgnoringTenant(joinPoint, ignoreTenant);
    }

    @Around("@within(ignoreTenant) && !@annotation(com.han.common.tenant.annotation.IgnoreTenant)")
    public Object aroundClass(ProceedingJoinPoint joinPoint, IgnoreTenant ignoreTenant) throws Throwable {
        return executeIgnoringTenant(joinPoint, ignoreTenant);
    }

    private Object executeIgnoringTenant(ProceedingJoinPoint joinPoint, IgnoreTenant ignoreTenant) throws Throwable {
        if (log.isDebugEnabled()) {
            log.debug("跳过租户过滤: {}, 理由={}", joinPoint.getSignature().toShortString(),
                    ignoreTenant == null || ignoreTenant.value().isBlank() ? "未填写" : ignoreTenant.value());
        }
        // 走 TenantHelper 的重入计数，保证与 TenantHelper.ignore 互相嵌套时外层不会被提前清掉
        TenantHelper.enterIgnore();
        try {
            return joinPoint.proceed();
        } finally {
            TenantHelper.exitIgnore();
        }
    }
}
