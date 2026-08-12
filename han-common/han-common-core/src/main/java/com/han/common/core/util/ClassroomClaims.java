package com.han.common.core.util;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 三个课堂兼容凭证的 payload 装配器，数字校园通路与本地账号通路共用同一套 claim 语义。
 *
 * <p>旧 api {@code UserInterceptor} 不验签、直接把 payload 反序列化成 {@code UserDto}，
 * 旧网关门户过滤器又要求 {@code userType} 等于 {@link #USER_TYPE}，因此：
 * {@code userType} 固定为门户要求的常量，业务身份类型另用 {@code roleType} 承载；
 * {@code userId} 始终序列化成字符串，避免旧侧 {@code String} 字段发生数字精度歧义。
 */
public final class ClassroomClaims {

    /** 旧网关 {@code PortalAuthGlobalFilter} 唯一接受的 userType 取值。 */
    public static final String USER_TYPE = "USER";

    private ClassroomClaims() {
    }

    public static Map<String, Object> build(String userId, String username, String roleType,
                                            Collection<String> roles, String identityId,
                                            String schoolId, String hanUserId) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("userId", text(userId));
        claims.put("username", text(username));
        claims.put("userType", USER_TYPE);
        claims.put("roleType", text(roleType));
        claims.put("roles", normalizeRoles(roles));
        claims.put("status", 0);
        claims.put("identityId", text(identityId));
        claims.put("schoolId", text(schoolId));
        claims.put("hanUserId", text(hanUserId));
        return claims;
    }

    /** 去重去空并保持传入顺序，供 {@code UserDto.roles} 消费。 */
    public static List<String> normalizeRoles(Collection<String> roles) {
        if (roles == null) {
            return List.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String role : roles) {
            if (role != null && !role.isBlank()) {
                normalized.add(role.trim());
            }
        }
        return List.copyOf(normalized);
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
