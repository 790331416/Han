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
 * <p>
 * MyBatis-Plus 的 {@link InterceptorIgnoreHelper} 内部是单值 ThreadLocal 而非栈，
 * {@code clearIgnoreStrategy()} 是无条件 remove。因此这里自行维护重入计数：
 * 只有最外层退出时才真正清除忽略标记，避免内层 ignore 结束时把外层的一起清掉，
 * 造成「同一个方法里前半段不过滤、后半段又过滤」这种极难定位的现象。
 * {@code IgnoreTenantAspect} 复用同一套计数，两种忽略机制可以互相嵌套。
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TenantHelper {

    /** 忽略租户过滤的嵌套层数，用于让忽略作用域可重入 */
    private static final ThreadLocal<Integer> IGNORE_DEPTH = new ThreadLocal<>();

    private static SecurityContext securityContext;

    /**
     * 设置 SecurityContext 实例（由 MybatisPlusConfig 初始化时调用）
     */
    public static void setSecurityContext(SecurityContext context) {
        securityContext = context;
    }

    /**
     * 进入忽略租户过滤的作用域，必须与 {@link #exitIgnore()} 在 finally 中配对使用。
     *
     * <p>仅供 {@code TenantHelper.ignore(...)} 与 {@code IgnoreTenantAspect} 使用，
     * 业务代码请直接用 {@link #ignore(Runnable)} / {@link #ignore(Supplier)}。</p>
     */
    public static void enterIgnore() {
        int depth = currentDepth() + 1;
        IGNORE_DEPTH.set(depth);
        if (depth == 1) {
            InterceptorIgnoreHelper.handle(IgnoreStrategy.builder().tenantLine(true).build());
        }
    }

    /**
     * 退出忽略租户过滤的作用域，最外层退出时才真正清除忽略标记。
     */
    public static void exitIgnore() {
        int depth = currentDepth() - 1;
        if (depth > 0) {
            IGNORE_DEPTH.set(depth);
            return;
        }
        IGNORE_DEPTH.remove();
        InterceptorIgnoreHelper.clearIgnoreStrategy();
    }

    /**
     * 当前线程是否处于忽略租户过滤的作用域内
     */
    public static boolean isIgnoring() {
        return currentDepth() > 0;
    }

    /**
     * 在忽略租户过滤的上下文中执行（无返回值）
     *
     * @param handle 执行逻辑
     */
    public static void ignore(Runnable handle) {
        enterIgnore();
        try {
            handle.run();
        } finally {
            exitIgnore();
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
        enterIgnore();
        try {
            return handle.get();
        } finally {
            exitIgnore();
        }
    }

    private static int currentDepth() {
        Integer depth = IGNORE_DEPTH.get();
        return depth == null ? 0 : depth;
    }

    /**
     * 获取当前租户ID
     */
    public static Long getTenantId() {
        return securityContext != null ? securityContext.getTenantId() : null;
    }
}
