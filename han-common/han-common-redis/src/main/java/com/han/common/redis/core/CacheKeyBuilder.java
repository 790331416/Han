package com.han.common.redis.core;

import com.han.common.core.constant.CacheConstants;

/**
 * 缓存 key 构建器。
 * <p>
 * 此前各调用点都是直接拼字符串，前缀不统一、租户维度时有时无。统一约定为
 * {@code han:{业务前缀}:{租户}:{标识}}，租户为空时退化为 {@code han:{业务前缀}:{标识}}。
 * <p>前缀请优先取 {@link CacheConstants} 里的常量，不要在业务代码里另写字面量。
 */
public final class CacheKeyBuilder {

    private static final String SEPARATOR = ":";

    private CacheKeyBuilder() {}

    /**
     * 构建不带租户维度的 key。
     *
     * @param prefix     业务前缀，建议取自 {@link CacheConstants}，可带或不带结尾冒号
     * @param identifier 业务标识
     */
    public static String build(String prefix, Object identifier) {
        return build(prefix, null, identifier);
    }

    /**
     * 构建带租户维度的 key。租户为空时退化为不带租户的形式，便于平台级数据复用同一套前缀。
     */
    public static String build(String prefix, Object tenantId, Object identifier) {
        StringBuilder sb = new StringBuilder(normalizePrefix(prefix));
        if (tenantId != null && !String.valueOf(tenantId).isBlank()) {
            sb.append(tenantId).append(SEPARATOR);
        }
        sb.append(identifier);
        return sb.toString();
    }

    /**
     * 构建按租户隔离的匹配模式，供 {@code SCAN} 使用（<b>不要</b>用 {@code KEYS}）。
     */
    public static String pattern(String prefix, Object tenantId) {
        StringBuilder sb = new StringBuilder(normalizePrefix(prefix));
        if (tenantId != null && !String.valueOf(tenantId).isBlank()) {
            sb.append(tenantId).append(SEPARATOR);
        }
        sb.append('*');
        return sb.toString();
    }

    private static String normalizePrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("缓存 key 前缀不能为空");
        }
        String trimmed = prefix.trim();
        if (!trimmed.startsWith(CacheConstants.CACHE_PREFIX)) {
            trimmed = CacheConstants.CACHE_PREFIX + trimmed;
        }
        return trimmed.endsWith(SEPARATOR) ? trimmed : trimmed + SEPARATOR;
    }
}
