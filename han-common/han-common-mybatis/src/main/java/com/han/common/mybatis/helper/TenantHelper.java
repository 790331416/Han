package com.han.common.mybatis.helper;

import com.baomidou.mybatisplus.core.plugins.IgnoreStrategy;
import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import com.han.common.core.context.SecurityContext;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.function.Supplier;

/**
 * 租户助手工具类
 * <p>
 * 提供忽略租户过滤的上下文执行能力。
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TenantHelper {

    private static SecurityContext securityContext;

    /**
     * 设置 SecurityContext 实例（由 MybatisPlusConfig 初始化时调用）
     */
    public static void setSecurityContext(SecurityContext context) {
        securityContext = context;
    }

    /**
     * 在忽略租户过滤的上下文中执行（无返回值）
     *
     * @param handle 执行逻辑
     */
    public static void ignore(Runnable handle) {
        InterceptorIgnoreHelper.handle(IgnoreStrategy.builder().tenantLine(true).build());
        try {
            handle.run();
        } finally {
            InterceptorIgnoreHelper.clearIgnoreStrategy();
        }
    }

    /**
     * 在忽略租户过滤的上下文中执行（有返回值）
     *
     * @param handle 执行逻辑
     * @param <T>    返回类型
     * @return 执行结果
     */
    public static <T> T ignore(Supplier<T> handle) {
        InterceptorIgnoreHelper.handle(IgnoreStrategy.builder().tenantLine(true).build());
        try {
            return handle.get();
        } finally {
            InterceptorIgnoreHelper.clearIgnoreStrategy();
        }
    }

    /**
     * 获取当前租户ID
     */
    public static Long getTenantId() {
        return securityContext != null ? securityContext.getTenantId() : null;
    }
}
