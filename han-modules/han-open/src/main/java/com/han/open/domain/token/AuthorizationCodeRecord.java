package com.han.open.domain.token;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/**
 * 授权码记录（存放于 Redis，取出即删）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthorizationCodeRecord implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 授权用户ID */
    private Long userId;

    /** 客户端标识（app_key） */
    private String clientId;

    /** 签发授权码时使用的回调地址，换票时必须完全一致 */
    private String redirectUri;

    /** 用户实际授予的授权范围（已与应用配置求交集） */
    private String scope;

    /** PKCE code_challenge */
    private String codeChallenge;

    /** PKCE code_challenge_method：S256 / plain */
    private String codeChallengeMethod;

    /** OpenID Connect nonce */
    private String nonce;

    /** 过期时间（epoch 秒） */
    private long expiresAt;

    @JsonIgnore
    public boolean isExpired() {
        return Instant.now().getEpochSecond() > expiresAt;
    }
}
