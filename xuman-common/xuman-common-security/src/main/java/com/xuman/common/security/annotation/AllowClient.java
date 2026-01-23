package com.xuman.common.security.annotation;

import com.xuman.common.core.enums.ClientType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 允许访问的客户端类型
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface AllowClient {

    /**
     * 允许的客户端类型
     */
    ClientType[] value();
}
