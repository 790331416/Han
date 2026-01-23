package com.xuman.common.security.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 防重复提交注解
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RepeatSubmit {

    /**
     * 间隔时间(秒)
     */
    int interval() default 5;

    /**
     * 提示消息
     */
    String message() default "请勿重复提交";
}
