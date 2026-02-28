package com.han.common.security.annotation;

import java.lang.annotation.*;

/**
 * Admin 控制器标识注解
 * 
 * <p>标注在控制器类上，表示该控制器为后台管理端接口，
 * 其中所有 @Override 的请求映射方法必须有权限注解
 * 
 * @author han
 * @since 1.0.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AdminAuth {
}
