package com.han.common.tenant.context;

import com.alibaba.ttl.TransmittableThreadLocal;

/**
 * 租户上下文持有者。
 *
 * <p><b>当前未启用</b>：运行期的租户来源是 {@code LoginUser.getTenantId()}（经
 * {@code SecurityContext} 暴露给 MyBatis 租户插件），本类在生产代码中没有写入点。
 * 新代码请不要读取本类来判断租户，否则会拿到恒为 null 的结果。</p>
 *
 * <p>另需注意：{@link TransmittableThreadLocal} 只有在挂了 TTL javaagent 或用
 * {@code TtlExecutors} 包装线程池时才真正生效，否则退化为普通的
 * {@code InheritableThreadLocal}。</p>
 */
public class TenantContextHolder {

    private static final TransmittableThreadLocal<Long> TENANT_ID = new TransmittableThreadLocal<>();

    private TenantContextHolder() {}

    /**
     * 获取当前租户ID
     */
    public static Long getTenantId() {
        return TENANT_ID.get();
    }

    /**
     * 设置当前租户ID
     */
    public static void setTenantId(Long tenantId) {
        TENANT_ID.set(tenantId);
    }

    /**
     * 清除租户ID
     */
    public static void clear() {
        TENANT_ID.remove();
    }

    /**
     * 是否有租户上下文
     */
    public static boolean hasTenant() {
        return TENANT_ID.get() != null;
    }
}
