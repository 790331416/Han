package com.xuman.open.domain.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;

/**
 * OAuth2授权请求DTO
 */
@Data
public class OAuth2AuthorizeDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 响应类型(code/token)
     */
    @NotBlank(message = "response_type不能为空")
    private String responseType;

    /**
     * 客户端ID
     */
    @NotBlank(message = "client_id不能为空")
    private String clientId;

    /**
     * 重定向URI
     */
    @NotBlank(message = "redirect_uri不能为空")
    private String redirectUri;

    /**
     * 授权范围
     */
    private String scope;

    /**
     * 状态(防CSRF)
     */
    private String state;

    /**
     * PKCE code_challenge
     */
    private String codeChallenge;

    /**
     * PKCE code_challenge_method (S256/plain)
     */
    private String codeChallengeMethod;

    /**
     * Nonce(OpenID Connect)
     */
    private String nonce;
}
