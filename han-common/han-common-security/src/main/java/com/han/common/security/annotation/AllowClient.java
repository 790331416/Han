package com.han.common.security.annotation;

import com.han.common.core.enums.ClientType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 允许访问的客户端类型
 *
 * @deprecated 没有任何切面实现这个注解，标了不会限制任何客户端，属于「写了不生效」的陷阱。
 * 保留声明仅为兼容既有引用，待确认无调用方后删除或补齐切面实现。
 */
@Deprecated
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface AllowClient {

    /**
     * 允许的客户端类型
     */
    ClientType[] value();
}
