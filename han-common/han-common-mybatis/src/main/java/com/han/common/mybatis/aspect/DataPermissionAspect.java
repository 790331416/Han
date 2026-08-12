package com.han.common.mybatis.aspect;

import com.han.common.mybatis.annotation.DataPermission;
import com.han.common.mybatis.context.DataPermissionContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * @DataPermission 注解 AOP 处理器
 * <p>
 * Mapper 方法上的注解由 {@code HanDataPermissionHandler} 直接按语句 ID 解析；
 * Service 方法上的注解拿不到语句 ID，需要本切面在方法执行期间把声明放进
 * {@link DataPermissionContextHolder}，供处理器读取。
 * <p>
 * 写法参照同包的 {@code IgnoreTenantAspect}：finally 中必须还原上下文。
 */
@Slf4j
@Aspect
@Component
public class DataPermissionAspect {

    @Around("@annotation(dataPermission)")
    public Object aroundMethod(ProceedingJoinPoint joinPoint, DataPermission dataPermission) throws Throwable {
        return executeWithDataPermission(joinPoint, dataPermission);
    }

    @Around("@within(dataPermission) && !@annotation(com.han.common.mybatis.annotation.DataPermission)")
    public Object aroundClass(ProceedingJoinPoint joinPoint, DataPermission dataPermission) throws Throwable {
        return executeWithDataPermission(joinPoint, dataPermission);
    }

    private Object executeWithDataPermission(ProceedingJoinPoint joinPoint, DataPermission dataPermission) throws Throwable {
        if (dataPermission == null) {
            return joinPoint.proceed();
        }
        if (log.isDebugEnabled()) {
            log.debug("启用数据权限过滤: {}", joinPoint.getSignature().toShortString());
        }
        DataPermissionContextHolder.push(dataPermission);
        try {
            return joinPoint.proceed();
        } finally {
            DataPermissionContextHolder.poll();
        }
    }
}
