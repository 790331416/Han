package com.han.common.security.annotation;

import java.lang.annotation.*;

/**
 * 内部服务调用认证注解
 * 
 * <p>标注在方法或类上，表示该接口仅允许内部微服务间调用，
 * 不对外暴露，由网关或内部鉴权机制校验
 * 
 * @author han
 * @since 1.0.0
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface InnerAuth {
}
