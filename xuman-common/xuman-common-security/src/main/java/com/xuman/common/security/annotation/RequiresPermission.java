package com.xuman.common.security.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 权限校验注解
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
     */
    Logical logical() default Logical.AND;

    enum Logical {
        AND, OR
    }
}
