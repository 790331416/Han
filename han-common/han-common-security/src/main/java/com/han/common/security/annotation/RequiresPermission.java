package com.han.common.security.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 权限校验注解
 *
 * <p>方法级与类级都会生效，方法级优先，见 {@code PermissionAspect}。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPermission {

    /**
     * 权限标识
     */
    String value();

    /**
     * 逻辑关系（多个权限时）
     *
     * @deprecated {@link #value()} 只接受单个权限标识，不存在「多个权限」的情形，
     * 该属性从未被切面读取。需要多权限组合请拆成多个注解或改用 {@code @PreAuthorize} 表达式。
     */
    @Deprecated
    Logical logical() default Logical.AND;

    enum Logical {
        AND, OR
    }
}
