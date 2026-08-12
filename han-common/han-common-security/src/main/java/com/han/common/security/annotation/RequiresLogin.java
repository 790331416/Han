package com.han.common.security.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 登录校验注解
 *
 * @deprecated 没有任何切面实现这个注解，标了也不会产生登录校验，属于「写了不生效」的陷阱。
 * 需要登录校验请使用 {@link RequiresPermission} 或 {@code @PreAuthorize}。
 * 保留声明仅为兼容既有引用，待确认无调用方后删除。
 */
@Deprecated
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresLogin {
}
