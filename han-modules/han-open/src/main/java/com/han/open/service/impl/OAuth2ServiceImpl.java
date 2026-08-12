package com.han.open.service.impl;

import com.han.common.core.exception.BusinessException;
import com.han.open.domain.dto.OAuth2AuthorizeDTO;
import com.han.open.domain.dto.OAuth2TokenDTO;
import com.han.open.domain.token.AccessTokenRecord;
import com.han.open.domain.token.AuthorizationCodeRecord;
import com.han.open.domain.token.RefreshTokenRecord;
import com.han.open.domain.vo.OpenAppVO;
import com.han.open.domain.vo.OAuth2TokenVO;
import com.han.open.domain.vo.OAuth2UserInfoVO;
import com.han.open.service.IOpenAppService;
import com.han.open.service.IOAuth2Service;
import com.han.open.store.IOAuth2TokenStore;
import com.han.open.util.PkceUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * OAuth2 授权服务实现。
 */
@Service
@RequiredArgsConstructor
public class OAuth2ServiceImpl implements IOAuth2Service {

    private static final int STATUS_ENABLED = 0;
    private static final int FLAG_ENABLED = 1;
    private static final long AUTHORIZATION_CODE_TTL_SECONDS = 300L;
    private static final long DEFAULT_ACCESS_TOKEN_TTL_SECONDS = 3600L;
    private static final long DEFAULT_REFRESH_TOKEN_TTL_SECONDS = 604800L;

    private final IOpenAppService openAppService;
    private final IOAuth2TokenStore tokenStore;

    @Override
    public String authorize(OAuth2AuthorizeDTO dto, Long userId) {
        OpenAppVO app = requireEnabledApp(dto.getClientId());
        if (!openAppService.validateRedirectUri(dto.getClientId(), dto.getRedirectUri())) {
            throw new BusinessException("redirect_uri 不合法");
        }
        String codeChallenge = StringUtils.hasText(dto.getCodeChallenge()) ? dto.getCodeChallenge().trim() : null;
        String codeChallengeMethod = PkceUtil.normalizeMethod(dto.getCodeChallengeMethod());
        requirePkceSatisfied(app, codeChallenge, codeChallengeMethod);
        String code = UUID.randomUUID().toString().replace("-", "");
        tokenStore.saveAuthorizationCode(code, AuthorizationCodeRecord.builder()
                .userId(userId)
                .clientId(app.getAppKey())
                .redirectUri(dto.getRedirectUri())
                .scope(dto.getScope())
                .codeChallenge(codeChallenge)
                .codeChallengeMethod(codeChallenge != null ? codeChallengeMethod : null)
                .nonce(dto.getNonce())
                .expiresAt(Instant.now().getEpochSecond() + AUTHORIZATION_CODE_TTL_SECONDS)
                .build(), Duration.ofSeconds(AUTHORIZATION_CODE_TTL_SECONDS));
        return code;
    }

    @Override
    public OAuth2TokenVO token(OAuth2TokenDTO dto) {
        if (dto == null) {
            throw new BusinessException("Token 请求不能为空");
        }
        String grantType = dto.getGrantType();
        if (grantType == null || grantType.isBlank()) {
            throw new BusinessException("grant_type 不能为空");
        }
        requireGrantTypeAllowed(dto.getClientId(), grantType);
        return switch (grantType) {
            case "authorization_code" -> issueAuthorizationCodeToken(dto);
            case "refresh_token" -> refreshToken(dto.getRefreshToken(), dto.getClientId(), dto.getClientSecret());
            case "client_credentials" -> issueClientCredentialsToken(dto);
            default -> throw new BusinessException("暂不支持的 grant_type: " + grantType);
        };
    }

    /**
     * 刷新令牌，采用轮换（rotation）策略：旧刷新令牌用后立即失效，并联动失效其配对的访问令牌。
     */
    @Override
    public OAuth2TokenVO refreshToken(String refreshToken, String clientId, String clientSecret) {
        if (!openAppService.validateClient(clientId, clientSecret)) {
            throw new BusinessException("客户端凭证无效");
        }
        RefreshTokenRecord record = tokenStore.getRefreshToken(refreshToken);
        if (record == null || record.isExpired() || !clientId.equals(record.getClientId())) {
            throw new BusinessException("RefreshToken 无效或已过期");
        }
        tokenStore.removeRefreshToken(refreshToken);
        tokenStore.removeAccessToken(record.getAccessToken());
        OpenAppVO app = requireEnabledApp(clientId);
        return buildToken(record.getUserId(), app, record.getScope());
    }

    @Override
    public void revokeToken(String token, String tokenTypeHint) {
        AccessTokenRecord accessTokenRecord = tokenStore.removeAccessToken(token);
        if (accessTokenRecord != null) {
            tokenStore.removeRefreshToken(accessTokenRecord.getRefreshToken());
            return;
        }
        RefreshTokenRecord refreshTokenRecord = tokenStore.removeRefreshToken(token);
        if (refreshTokenRecord != null) {
            tokenStore.removeAccessToken(refreshTokenRecord.getAccessToken());
        }
    }

    @Override
    public Object introspectToken(String token) {
        AccessTokenRecord record = tokenStore.getAccessToken(token);
        if (record == null || record.isExpired()) {
            tokenStore.removeAccessToken(token);
            return Map.of("active", false);
        }
        return Map.of(
                "active", true,
                "client_id", record.getClientId(),
                "scope", record.getScope(),
                "exp", record.getExpiresAt(),
                "sub", String.valueOf(record.getUserId())
        );
    }

    @Override
    public OAuth2UserInfoVO getUserInfo(String accessToken) {
        AccessTokenRecord record = requireActiveAccessToken(accessToken);
        return OAuth2UserInfoVO.builder()
                .sub(String.valueOf(record.getUserId()))
                .name(record.getUserId() == 0L ? record.getClientId() : "user-" + record.getUserId())
                .nickname(record.getUserId() == 0L ? record.getClientId() : "用户" + record.getUserId())
                .build();
    }

    @Override
    public Long validateAuthorizationCode(String code, String clientId, String redirectUri, String codeVerifier) {
        AuthorizationCodeRecord record = tokenStore.consumeAuthorizationCode(code);
        if (record == null || record.isExpired()) {
            return null;
        }
        if (!record.getClientId().equals(clientId) || !record.getRedirectUri().equals(redirectUri)) {
            return null;
        }
        if (StringUtils.hasText(record.getCodeChallenge())
                && !PkceUtil.matches(codeVerifier, record.getCodeChallenge(), record.getCodeChallengeMethod())) {
            return null;
        }
        return record.getUserId();
    }

    /**
     * 应用开启 {@code require_pkce} 时，授权阶段必须携带 S256 挑战。
     *
     * <p>RFC 7636 的 plain 方法不能抵御授权码拦截攻击，因此强制开关下只接受 S256。
     */
    private void requirePkceSatisfied(OpenAppVO app, String codeChallenge, String codeChallengeMethod) {
        boolean requirePkce = app.getRequirePkce() != null && app.getRequirePkce() == FLAG_ENABLED;
        if (!requirePkce) {
            return;
        }
        if (codeChallenge == null) {
            throw new BusinessException("该应用已强制启用 PKCE，必须提供 code_challenge");
        }
        if (!PkceUtil.METHOD_S256.equals(codeChallengeMethod)) {
            throw new BusinessException("该应用已强制启用 PKCE，code_challenge_method 必须为 S256");
        }
    }

    private AccessTokenRecord requireActiveAccessToken(String accessToken) {
        AccessTokenRecord record = tokenStore.getAccessToken(accessToken);
        if (record == null || record.isExpired()) {
            tokenStore.removeAccessToken(accessToken);
            throw new BusinessException("AccessToken 无效或已过期");
        }
        return record;
    }

    private OAuth2TokenVO issueAuthorizationCodeToken(OAuth2TokenDTO dto) {
        if (!openAppService.validateClient(dto.getClientId(), dto.getClientSecret())) {
            throw new BusinessException("客户端凭证无效");
        }
        OpenAppVO app = requireEnabledApp(dto.getClientId());
        Long userId = validateAuthorizationCode(dto.getCode(), dto.getClientId(), dto.getRedirectUri(), dto.getCodeVerifier());
        if (userId == null) {
            throw new BusinessException("授权码无效或已过期");
        }
        return buildToken(userId, app, dto.getScope());
    }

    private OAuth2TokenVO issueClientCredentialsToken(OAuth2TokenDTO dto) {
        if (!openAppService.validateClient(dto.getClientId(), dto.getClientSecret())) {
            throw new BusinessException("客户端凭证无效");
        }
        OpenAppVO app = requireEnabledApp(dto.getClientId());
        return buildToken(0L, app, dto.getScope());
    }

    private OAuth2TokenVO buildToken(Long userId, OpenAppVO app, String scope) {
        long accessTokenTtl = app.getAccessTokenTtl() != null ? app.getAccessTokenTtl() : DEFAULT_ACCESS_TOKEN_TTL_SECONDS;
        long refreshTokenTtl = app.getRefreshTokenTtl() != null ? app.getRefreshTokenTtl() : DEFAULT_REFRESH_TOKEN_TTL_SECONDS;
        String resolvedScope = scope != null ? scope : String.join(" ", app.getScopes() != null ? app.getScopes() : List.of());
        String accessToken = UUID.randomUUID().toString().replace("-", "");
        String refreshToken = UUID.randomUUID().toString().replace("-", "");
        long now = Instant.now().getEpochSecond();

        tokenStore.saveAccessToken(accessToken, AccessTokenRecord.builder()
                .userId(userId)
                .clientId(app.getAppKey())
                .scope(resolvedScope)
                .refreshToken(refreshToken)
                .expiresAt(now + accessTokenTtl)
                .build(), Duration.ofSeconds(accessTokenTtl));
        tokenStore.saveRefreshToken(refreshToken, RefreshTokenRecord.builder()
                .userId(userId)
                .clientId(app.getAppKey())
                .scope(resolvedScope)
                .accessToken(accessToken)
                .expiresAt(now + refreshTokenTtl)
                .build(), Duration.ofSeconds(refreshTokenTtl));
        tokenStore.indexUserToken(app.getAppKey(), userId, accessToken, Duration.ofSeconds(refreshTokenTtl));

        return OAuth2TokenVO.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresIn(accessTokenTtl)
                .refreshToken(refreshToken)
                .scope(resolvedScope)
                .build();
    }

    private OpenAppVO requireEnabledApp(String clientId) {
        OpenAppVO app = openAppService.getAppByAppKey(clientId);
        if (app == null || app.getStatus() == null || app.getStatus() != STATUS_ENABLED) {
            throw new BusinessException("客户端不存在或已停用");
        }
        return app;
    }

    /**
     * 按应用配置校验 grant_type：应用配置了授权类型列表时，仅允许列表内的类型签发。
     * 列表为空视为未收紧配置，保持放行以兼容存量应用。
     */
    private void requireGrantTypeAllowed(String clientId, String grantType) {
        OpenAppVO app = requireEnabledApp(clientId);
        List<String> grantTypes = app.getGrantTypes();
        if (grantTypes != null && !grantTypes.isEmpty() && !grantTypes.contains(grantType)) {
            throw new BusinessException("该应用未启用此授权类型: " + grantType);
        }
    }
}
