package com.han.common.security.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 限流注解
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimiter {

    /**
     * 限流key
     */
    String key() default "";

    /**
     * 限流时间(秒)
     */
    int time() default 60;

    /**
     * 限流次数
     */
    int count() default 100;

    /**
     * 限流类型
     */
    LimitType limitType() default LimitType.DEFAULT;

    enum LimitType {
        /** 默认（全局） */
        DEFAULT,
        /** 按IP限流 */
        IP,
        /** 按用户限流 */
        USER
    }
}
