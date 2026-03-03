package com.han.open.domain.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * OAuth2 Token响应VO
 */
@Data
@Builder
public class OAuth2TokenVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 访问令牌
     */
    @JsonProperty("access_token")
    private String accessToken;

    /**
     * 令牌类型
     */
    @JsonProperty("token_type")
    private String tokenType;

    /**
     * 有效期(秒)
     */
    @JsonProperty("expires_in")
    private long expiresIn;

    /**
     * 刷新令牌
     */
    @JsonProperty("refresh_token")
    private String refreshToken;

    /**
     * 授权范围
     */
    private String scope;

    /**
     * ID Token(OpenID Connect)
     */
    @JsonProperty("id_token")
    private String idToken;
}
