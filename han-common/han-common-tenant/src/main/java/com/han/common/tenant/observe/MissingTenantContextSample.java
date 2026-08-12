package com.han.common.tenant.observe;

/**
 * 无租户上下文观测样本。
 *
 * @param operation 操作类型，例如 SQL / INSERT
 * @param tableName 目标表名
 * @param callSite  调用点（类名#方法名:行号）
 * @param count     累计次数
 */
public record MissingTenantContextSample(String operation, String tableName, String callSite, long count) {
}
