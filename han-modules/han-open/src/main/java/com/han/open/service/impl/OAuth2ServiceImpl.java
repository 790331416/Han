package com.han.open.service.impl;

import com.han.common.core.exception.BusinessException;
import com.han.common.core.util.HanJsonUtil;
import com.han.open.domain.dto.OAuth2AuthorizeDTO;
import com.han.open.domain.dto.OAuth2TokenDTO;
import com.han.open.domain.vo.OpenAppVO;
import com.han.open.domain.vo.OpenAccessTokenContext;
import com.han.open.domain.vo.OAuth2TokenVO;
import com.han.open.domain.vo.OAuth2UserInfoVO;
import com.han.open.service.IOpenAppService;
import com.han.open.service.IOAuth2Service;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
    private static final String AUTHORIZATION_CODE_KEY = "han:open:oauth2:code:";
    private static final String ACCESS_TOKEN_KEY = "han:open:oauth2:access:";
    private static final String REFRESH_TOKEN_KEY = "han:open:oauth2:refresh:";

    private final IOpenAppService openAppService;
    private final StringRedisTemplate redisTemplate;

    @Override
    public String authorize(OAuth2AuthorizeDTO dto, Long userId) {
        OpenAppVO app = requireEnabledApp(dto.getClientId());
        if (!openAppService.validateRedirectUri(dto.getClientId(), dto.getRedirectUri())) {
            throw new BusinessException("redirect_uri 不合法");
        }
        String code = UUID.randomUUID().toString().replace("-", "");
        AuthorizationCodeRecord record = new AuthorizationCodeRecord(
                userId,
                app.getAppKey(),
                dto.getRedirectUri(),
                resolveScope(app, dto.getScope()),
                dto.getCodeChallenge(),
                Instant.now().plusSeconds(AUTHORIZATION_CODE_TTL_SECONDS));
        write(AUTHORIZATION_CODE_KEY + code, record, AUTHORIZATION_CODE_TTL_SECONDS);
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
        RefreshTokenRecord record = read(REFRESH_TOKEN_KEY + refreshToken, RefreshTokenRecord.class);
        if (record == null || !record.clientId().equals(clientId)) {
            redisTemplate.delete(REFRESH_TOKEN_KEY + refreshToken);
            throw new BusinessException("RefreshToken 无效或已过期");
        }
        OpenAppVO app = requireEnabledApp(clientId);
        return buildToken(record.userId(), app, record.scope());
    }

    @Override
    public void revokeToken(String token, String tokenTypeHint, String clientId, String clientSecret) {
        requireClient(clientId, clientSecret);
        AccessTokenRecord accessTokenRecord = read(ACCESS_TOKEN_KEY + token, AccessTokenRecord.class);
        if (accessTokenRecord != null) {
            requireSameClient(accessTokenRecord.clientId(), clientId);
            redisTemplate.delete(List.of(ACCESS_TOKEN_KEY + token, REFRESH_TOKEN_KEY + accessTokenRecord.refreshToken()));
            return;
        }
        RefreshTokenRecord refreshTokenRecord = read(REFRESH_TOKEN_KEY + token, RefreshTokenRecord.class);
        if (refreshTokenRecord != null) {
            requireSameClient(refreshTokenRecord.clientId(), clientId);
        }
        redisTemplate.delete(REFRESH_TOKEN_KEY + token);
    }

    @Override
    public Object introspectToken(String token, String clientId, String clientSecret) {
        requireClient(clientId, clientSecret);
        AccessTokenRecord record = read(ACCESS_TOKEN_KEY + token, AccessTokenRecord.class);
        if (record == null) {
            redisTemplate.delete(ACCESS_TOKEN_KEY + token);
            return Map.of("active", false);
        }
        requireSameClient(record.clientId(), clientId);
        return Map.of(
                "active", true,
                "client_id", record.clientId(),
                "scope", record.scope(),
                "exp", record.expiresAt().getEpochSecond(),
                "sub", String.valueOf(record.userId())
        );
    }

    @Override
    public OpenAccessTokenContext requireAccessToken(String accessToken, String requiredScope) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new BusinessException("AccessToken 不能为空");
        }
        AccessTokenRecord record = read(ACCESS_TOKEN_KEY + accessToken.trim(), AccessTokenRecord.class);
        if (record == null) {
            throw new BusinessException("AccessToken 无效或已过期");
        }
        OpenAppVO app = requireEnabledApp(record.clientId());
        if (!applicationVersion(app).equals(record.applicationVersion())) {
            redisTemplate.delete(List.of(ACCESS_TOKEN_KEY + accessToken.trim(), REFRESH_TOKEN_KEY + record.refreshToken()));
            throw new BusinessException("应用授权已变更，请重新获取 AccessToken");
        }
        Set<String> scopes = scopeSet(record.scope());
        if (requiredScope != null && !requiredScope.isBlank() && !scopes.contains(requiredScope)) {
            throw new BusinessException("应用未获授权范围: " + requiredScope);
        }
        return new OpenAccessTokenContext(record.userId(), record.tenantId(), record.clientId(), scopes,
                record.schoolIds(), record.applicationVersion(), record.refreshToken());
    }

    @Override
    public OAuth2UserInfoVO getUserInfo(String accessToken) {
        AccessTokenRecord record = read(ACCESS_TOKEN_KEY + accessToken, AccessTokenRecord.class);
        if (record == null) {
            redisTemplate.delete(ACCESS_TOKEN_KEY + accessToken);
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
        AuthorizationCodeRecord record = read(AUTHORIZATION_CODE_KEY + code, AuthorizationCodeRecord.class);
        redisTemplate.delete(AUTHORIZATION_CODE_KEY + code);
        if (record == null) {
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
        String resolvedScope = resolveScope(app, scope);
        String accessToken = UUID.randomUUID().toString().replace("-", "");
        String refreshToken = UUID.randomUUID().toString().replace("-", "");
        String applicationVersion = applicationVersion(app);
        AccessTokenRecord access = new AccessTokenRecord(
                userId,
                app.getAppKey(),
                resolvedScope,
                refreshToken,
                app.getTenantId(),
                app.getSchoolIds() == null ? List.of() : app.getSchoolIds(),
                applicationVersion,
                Instant.now().plusSeconds(accessTokenTtl));
        RefreshTokenRecord refresh = new RefreshTokenRecord(
                userId,
                app.getAppKey(),
                resolvedScope,
                Instant.now().plusSeconds(refreshTokenTtl));
        write(ACCESS_TOKEN_KEY + accessToken, access, accessTokenTtl);
        write(REFRESH_TOKEN_KEY + refreshToken, refresh, refreshTokenTtl);
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

    private void requireClient(String clientId, String clientSecret) {
        if (!openAppService.validateClient(clientId, clientSecret)) {
            throw new BusinessException("客户端凭证无效");
        }
    }

    private static void requireSameClient(String tokenClientId, String requestingClientId) {
        if (tokenClientId == null || !tokenClientId.equals(requestingClientId)) {
            throw new BusinessException("客户端无权操作该 Token");
        }
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

    private String resolveScope(OpenAppVO app, String requestedScope) {
        Set<String> allowed = scopeSet(String.join(" ", app.getScopes() == null ? List.of() : app.getScopes()));
        Set<String> requested = requestedScope == null || requestedScope.isBlank() ? allowed : scopeSet(requestedScope);
        if (!allowed.containsAll(requested)) {
            throw new BusinessException("请求的 Scope 超出应用授权范围");
        }
        return String.join(" ", requested);
    }

    private static Set<String> scopeSet(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        Set<String> scopes = new LinkedHashSet<>();
        for (String item : value.split("[ ,]+")) {
            if (!item.isBlank()) {
                scopes.add(item.trim());
            }
        }
        return Set.copyOf(scopes);
    }

    private static String applicationVersion(OpenAppVO app) {
        return app.getUpdateTime() == null ? "" : app.getUpdateTime().toString();
    }

    private <T> void write(String key, T value, long ttlSeconds) {
        redisTemplate.opsForValue().set(key, HanJsonUtil.toJsonString(value), Duration.ofSeconds(ttlSeconds));
    }

    private <T> T read(String key, Class<T> type) {
        String value = redisTemplate.opsForValue().get(key);
        return HanJsonUtil.parseObject(value, type);
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
            Long tenantId,
            List<Long> schoolIds,
            String applicationVersion,
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
