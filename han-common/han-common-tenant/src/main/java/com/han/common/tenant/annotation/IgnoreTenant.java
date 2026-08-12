package com.han.common.tenant.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 忽略租户过滤。
 *
 * <p>用于「整个方法天然就是跨租户」的场景（例如登录时按用户名跨租户找候选、
 * 调度器启动时加载全部租户的任务、平台扫描全部租户）。声明式、可被静态扫描，
 * 便于建立忽略点清单并纳入安全评审。</p>
 *
 * <p>如果一个方法里只有一两条语句需要跨租户，请改用 {@code TenantHelper.ignore}
 * 把忽略范围收到最小语句粒度，不要把整个方法打开。</p>
 *
 * <p>禁止把本注解打在 Controller 类或 Service 类上：切面虽然支持类级
 * （{@code @within}），但那等于给整个类开后门。</p>
 *
 * <p>纪律：每个忽略点都必须说明「为什么这里拿不到租户」，请通过 {@link #value()}
 * 填写中文理由，无理由的忽略点视为不合规。</p>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface IgnoreTenant {

    /**
     * 忽略租户过滤的中文理由，例如「登录时租户未知，必须跨租户查找候选用户」。
     */
    String value() default "";
}
