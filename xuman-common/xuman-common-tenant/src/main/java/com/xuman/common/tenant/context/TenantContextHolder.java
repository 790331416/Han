package com.xuman.common.tenant.context;

import com.alibaba.ttl.TransmittableThreadLocal;

/**
 * 租户上下文持有者
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
