package com.han.common.security.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 角色校验注解
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresRole {

    /**
     * 角色标识
     */
    String[] value();

    /**
     * 逻辑关系
     */
    Logical logical() default Logical.AND;

    enum Logical {
        AND, OR
    }
}
