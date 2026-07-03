package com.han.open.service.impl;

import com.han.common.core.exception.BusinessException;
import com.han.open.domain.dto.OAuth2AuthorizeDTO;
import com.han.open.domain.dto.OAuth2TokenDTO;
import com.han.open.domain.vo.OpenAppVO;
import com.han.open.domain.vo.OAuth2TokenVO;
import com.han.open.domain.vo.OAuth2UserInfoVO;
import com.han.open.service.IOpenAppService;
import com.han.open.service.IOAuth2Service;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OAuth2 授权服务实现。
 */
@Service
@RequiredArgsConstructor
public class OAuth2ServiceImpl implements IOAuth2Service {

    private static final int STATUS_ENABLED = 0;
    private static final long AUTHORIZATION_CODE_TTL_SECONDS = 300L;
    private static final long DEFAULT_ACCESS_TOKEN_TTL_SECONDS = 3600L;
    private static final long DEFAULT_REFRESH_TOKEN_TTL_SECONDS = 604800L;

    private final IOpenAppService openAppService;

    private final Map<String, AuthorizationCodeRecord> authCodeStore = new ConcurrentHashMap<>();
    private final Map<String, AccessTokenRecord> tokenStore = new ConcurrentHashMap<>();
    private final Map<String, RefreshTokenRecord> refreshTokenStore = new ConcurrentHashMap<>();

    @Override
    public String authorize(OAuth2AuthorizeDTO dto, Long userId) {
        OpenAppVO app = requireEnabledApp(dto.getClientId());
        if (!openAppService.validateRedirectUri(dto.getClientId(), dto.getRedirectUri())) {
            throw new BusinessException("redirect_uri 不合法");
        }
        String code = UUID.randomUUID().toString().replace("-", "");
        authCodeStore.put(code, new AuthorizationCodeRecord(
                userId,
                app.getAppKey(),
                dto.getRedirectUri(),
                dto.getScope(),
                dto.getCodeChallenge(),
                Instant.now().plusSeconds(AUTHORIZATION_CODE_TTL_SECONDS)
        ));
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

    @Override
    public OAuth2TokenVO refreshToken(String refreshToken, String clientId, String clientSecret) {
        if (!openAppService.validateClient(clientId, clientSecret)) {
            throw new BusinessException("客户端凭证无效");
        }
        RefreshTokenRecord record = refreshTokenStore.get(refreshToken);
        if (record == null || record.isExpired() || !record.clientId().equals(clientId)) {
            refreshTokenStore.remove(refreshToken);
            throw new BusinessException("RefreshToken 无效或已过期");
        }
        OpenAppVO app = requireEnabledApp(clientId);
        return buildToken(record.userId(), app, record.scope());
    }

    @Override
    public void revokeToken(String token, String tokenTypeHint) {
        AccessTokenRecord accessTokenRecord = tokenStore.remove(token);
        if (accessTokenRecord != null) {
            refreshTokenStore.remove(accessTokenRecord.refreshToken());
            return;
        }
        refreshTokenStore.remove(token);
    }

    @Override
    public Object introspectToken(String token) {
        AccessTokenRecord record = tokenStore.get(token);
        if (record == null || record.isExpired()) {
            tokenStore.remove(token);
            return Map.of("active", false);
        }
        return Map.of(
                "active", true,
                "client_id", record.clientId(),
                "scope", record.scope(),
                "exp", record.expiresAt().getEpochSecond(),
                "sub", String.valueOf(record.userId())
        );
    }

    @Override
    public OAuth2UserInfoVO getUserInfo(String accessToken) {
        AccessTokenRecord record = tokenStore.get(accessToken);
        if (record == null || record.isExpired()) {
            tokenStore.remove(accessToken);
            throw new BusinessException("AccessToken 无效或已过期");
        }
        return OAuth2UserInfoVO.builder()
                .sub(String.valueOf(record.userId()))
                .name(record.userId() == 0L ? record.clientId() : "user-" + record.userId())
                .nickname(record.userId() == 0L ? record.clientId() : "用户" + record.userId())
                .build();
    }

    @Override
    public Long validateAuthorizationCode(String code, String clientId, String redirectUri, String codeVerifier) {
        AuthorizationCodeRecord record = authCodeStore.remove(code);
        if (record == null || record.isExpired()) {
            return null;
        }
        if (!record.clientId().equals(clientId) || !record.redirectUri().equals(redirectUri)) {
            return null;
        }
        if (record.codeChallenge() != null && !record.codeChallenge().isBlank()) {
            if (codeVerifier == null || codeVerifier.isBlank()) {
                return null;
            }
        }
        return record.userId();
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
        tokenStore.put(accessToken, new AccessTokenRecord(
                userId,
                app.getAppKey(),
                resolvedScope,
                refreshToken,
                Instant.now().plusSeconds(accessTokenTtl)
        ));
        refreshTokenStore.put(refreshToken, new RefreshTokenRecord(
                userId,
                app.getAppKey(),
                resolvedScope,
                Instant.now().plusSeconds(refreshTokenTtl)
        ));
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

    private record AuthorizationCodeRecord(
            Long userId,
            String clientId,
            String redirectUri,
            String scope,
            String codeChallenge,
            Instant expiresAt
    ) {
        private boolean isExpired() {
            return expiresAt.isBefore(Instant.now());
        }
    }

    private record AccessTokenRecord(
            Long userId,
            String clientId,
            String scope,
            String refreshToken,
            Instant expiresAt
    ) {
        private boolean isExpired() {
            return expiresAt.isBefore(Instant.now());
        }
    }

    private record RefreshTokenRecord(
            Long userId,
            String clientId,
            String scope,
            Instant expiresAt
    ) {
        private boolean isExpired() {
            return expiresAt.isBefore(Instant.now());
        }
    }
}
