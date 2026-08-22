package com.han.open.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Set;

/** 已验证开放平台 AccessToken 的授权上下文，只在服务端使用。 */
public record OpenAccessTokenContext(
        Long userId,
        Long tenantId,
        String clientId,
        Set<String> scopes,
        List<Long> schoolIds,
        String applicationVersion,
        String refreshToken,
        Long appId,
        String environment
) implements Serializable {

    /** 兼容已有调用方构造的旧上下文，旧 Token 按生产环境处理。 */
    public OpenAccessTokenContext(Long userId, Long tenantId, String clientId,
                                  Set<String> scopes, List<Long> schoolIds,
                                  String applicationVersion, String refreshToken) {
        this(userId, tenantId, clientId, scopes, schoolIds, applicationVersion, refreshToken, null, "PROD");
    }

    @Serial
    private static final long serialVersionUID = 1L;
}
