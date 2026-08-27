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
import com.han.open.service.OpenAppAuthorizationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * OAuth2 授权服务实现。
 */
@Service
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
    private final OpenAppAuthorizationService authorizationService;

    @Autowired
    public OAuth2ServiceImpl(IOpenAppService openAppService,
                             StringRedisTemplate redisTemplate,
                             OpenAppAuthorizationService authorizationService) {
        this.openAppService = openAppService;
        this.redisTemplate = redisTemplate;
        this.authorizationService = authorizationService;
    }

    /** 保留旧单元测试和旧调用方的两参数构造入口。 */
    public OAuth2ServiceImpl(IOpenAppService openAppService, StringRedisTemplate redisTemplate) {
        this(openAppService, redisTemplate, null);
    }

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
        return switch (grantType) {
            case "authorization_code" -> issueAuthorizationCodeToken(dto);
            case "refresh_token" -> refreshToken(dto.getRefreshToken(), dto.getClientId(), dto.getClientSecret());
            case "client_credentials" -> issueClientCredentialsToken(dto);
            default -> throw new BusinessException("暂不支持的 grant_type: " + grantType);
        };
    }

    @Override
    public OAuth2TokenVO refreshToken(String refreshToken, String clientId, String clientSecret) {
        ClientAuthentication authentication = authenticateClient(clientId, clientSecret);
        RefreshTokenRecord record = read(REFRESH_TOKEN_KEY + refreshToken, RefreshTokenRecord.class);
        if (record == null || !Objects.equals(record.clientId(), authentication.clientId()) || record.isExpired()) {
            redisTemplate.delete(REFRESH_TOKEN_KEY + refreshToken);
            throw new BusinessException("RefreshToken 无效或已过期");
        }
        requireGrantTypeAllowed(authentication.app(), "refresh_token");
        return buildToken(record.userId(), authentication.app(), record.scope(),
                authentication.clientId(), authentication.appId(), authentication.environment());
    }

    @Override
    public void revokeToken(String token, String tokenTypeHint, String clientId, String clientSecret) {
        ClientAuthentication authentication = authenticateClient(clientId, clientSecret);
        AccessTokenRecord accessTokenRecord = read(ACCESS_TOKEN_KEY + token, AccessTokenRecord.class);
        if (accessTokenRecord != null) {
            requireSameClient(accessTokenRecord.clientId(), authentication.clientId());
            redisTemplate.delete(List.of(ACCESS_TOKEN_KEY + token, REFRESH_TOKEN_KEY + accessTokenRecord.refreshToken()));
            return;
        }
        RefreshTokenRecord refreshTokenRecord = read(REFRESH_TOKEN_KEY + token, RefreshTokenRecord.class);
        if (refreshTokenRecord != null) {
            requireSameClient(refreshTokenRecord.clientId(), authentication.clientId());
        }
        redisTemplate.delete(REFRESH_TOKEN_KEY + token);
    }

    @Override
    public Object introspectToken(String token, String clientId, String clientSecret) {
        ClientAuthentication authentication = authenticateClient(clientId, clientSecret);
        AccessTokenRecord record = read(ACCESS_TOKEN_KEY + token, AccessTokenRecord.class);
        if (record == null || record.isExpired()) {
            redisTemplate.delete(ACCESS_TOKEN_KEY + token);
            return Map.of("active", false);
        }
        requireSameClient(record.clientId(), authentication.clientId());
        return Map.of(
                "active", true,
                "client_id", record.clientId(),
                "app_id", record.appId() == null ? authentication.appId() : record.appId(),
                "environment", record.environment() == null ? authentication.environment() : record.environment(),
                "scope", record.scope(),
                "exp", record.expiresAt().getEpochSecond(),
                "sub", String.valueOf(record.userId())
        );
    }

    @Override
    public OpenAccessTokenContext requireAccessToken(String accessToken, String requiredScope) {
        return requireAccessToken(accessToken, requiredScope, null, false);
    }

    @Override
    public OpenAccessTokenContext requireAccessToken(String accessToken, String requiredScope, String resourceCode) {
        return requireAccessToken(accessToken, requiredScope, resourceCode, true);
    }

    private OpenAccessTokenContext requireAccessToken(String accessToken, String requiredScope,
                                                      String resourceCode, boolean requireResource) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new BusinessException("AccessToken 不能为空");
        }
        String token = accessToken.trim();
        AccessTokenRecord record = read(ACCESS_TOKEN_KEY + token, AccessTokenRecord.class);
        if (record == null || record.isExpired()) {
            redisTemplate.delete(ACCESS_TOKEN_KEY + token);
            throw new BusinessException("AccessToken 无效或已过期");
        }
        OpenAppVO app = resolveTokenApp(record);
        Long appId = record.appId() != null ? record.appId() : app.getAppId();
        String environment = normalizeEnvironment(record.environment());
        requireUsableApp(app, environment);
        if (!applicationVersion(app).equals(record.applicationVersion())) {
            redisTemplate.delete(List.of(ACCESS_TOKEN_KEY + token, REFRESH_TOKEN_KEY + record.refreshToken()));
            throw new BusinessException("应用授权已变更，请重新获取 AccessToken");
        }
        Set<String> scopes = scopeSet(record.scope());
        if (requiredScope != null && !requiredScope.isBlank() && !scopes.contains(requiredScope)) {
            throw new BusinessException("应用未获授权范围: " + requiredScope);
        }
        // 学校范围只有一个来源：应用管理中的授权学校。接口授权只控制资源、环境和有效期。
        List<Long> schoolIds = app.getSchoolIds() == null ? List.of() : app.getSchoolIds();
        if (app.getVendorId() != null && StringUtils.hasText(requiredScope)) {
            if (authorizationService == null || appId == null || record.tenantId() == null) {
                throw new BusinessException("应用授权上下文缺失");
            }
            String grant;
            if (requireResource) {
                if (!StringUtils.hasText(resourceCode)) {
                    throw new BusinessException("资源编码不能为空");
                }
                grant = authorizationService.resolveAuthorizedDataScope(
                        record.tenantId(), appId, environment, requiredScope.trim(), resourceCode.trim());
            } else {
                grant = authorizationService.resolveAuthorizedDataScope(
                        record.tenantId(), appId, environment, requiredScope.trim());
            }
            if (grant == null) {
                throw new BusinessException("应用未获授权资源或授权已失效");
            }
        }
        return new OpenAccessTokenContext(record.userId(), record.tenantId(), record.clientId(), scopes,
                schoolIds, record.applicationVersion(), record.refreshToken(), appId, environment);
    }

    @Override
    public OAuth2UserInfoVO getUserInfo(String accessToken) {
        AccessTokenRecord record = read(ACCESS_TOKEN_KEY + accessToken, AccessTokenRecord.class);
        if (record == null) {
            redisTemplate.delete(ACCESS_TOKEN_KEY + accessToken);
            throw new BusinessException("AccessToken 无效或已过期");
        }
        Set<String> scopes = scopeSet(record.scope());
        if (!scopes.contains("openid")) {
            throw new BusinessException("userinfo 需要 openid Scope");
        }
        OAuth2UserInfoVO.OAuth2UserInfoVOBuilder builder = OAuth2UserInfoVO.builder()
                .sub(String.valueOf(record.userId()));
        if (scopes.contains("profile")) {
            builder.name(record.userId() == 0L ? record.clientId() : "user-" + record.userId())
                    .nickname(record.userId() == 0L ? record.clientId() : "用户" + record.userId());
        }
        return builder.build();
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
        ClientAuthentication authentication = authenticateClient(dto.getClientId(), dto.getClientSecret());
        requireGrantTypeAllowed(authentication.app(), "authorization_code");
        Long userId = validateAuthorizationCode(dto.getCode(), dto.getClientId(), dto.getRedirectUri(), dto.getCodeVerifier());
        if (userId == null) {
            throw new BusinessException("授权码无效或已过期");
        }
        return buildToken(userId, authentication.app(), dto.getScope(), authentication.clientId(),
                authentication.appId(), authentication.environment());
    }

    private OAuth2TokenVO issueClientCredentialsToken(OAuth2TokenDTO dto) {
        ClientAuthentication authentication = authenticateClient(dto.getClientId(), dto.getClientSecret());
        requireGrantTypeAllowed(authentication.app(), "client_credentials");
        return buildToken(0L, authentication.app(), dto.getScope(), authentication.clientId(),
                authentication.appId(), authentication.environment());
    }

    private OAuth2TokenVO buildToken(Long userId, OpenAppVO app, String scope,
                                     String clientId, Long appId, String environment) {
        long accessTokenTtl = app.getAccessTokenTtl() != null ? app.getAccessTokenTtl() : DEFAULT_ACCESS_TOKEN_TTL_SECONDS;
        long refreshTokenTtl = app.getRefreshTokenTtl() != null ? app.getRefreshTokenTtl() : DEFAULT_REFRESH_TOKEN_TTL_SECONDS;
        String resolvedScope = resolveScope(app, scope);
        String accessToken = UUID.randomUUID().toString().replace("-", "");
        String refreshToken = UUID.randomUUID().toString().replace("-", "");
        String applicationVersion = applicationVersion(app);
        AccessTokenRecord access = new AccessTokenRecord(
                userId,
                clientId,
                resolvedScope,
                refreshToken,
                app.getTenantId(),
                app.getSchoolIds() == null ? List.of() : app.getSchoolIds(),
                applicationVersion,
                Instant.now().plusSeconds(accessTokenTtl),
                appId,
                environment);
        RefreshTokenRecord refresh = new RefreshTokenRecord(
                userId,
                clientId,
                resolvedScope,
                Instant.now().plusSeconds(refreshTokenTtl),
                appId,
                environment);
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

    private ClientAuthentication authenticateClient(String clientId, String clientSecret) {
        if (!StringUtils.hasText(clientId) || !StringUtils.hasText(clientSecret)) {
            throw new BusinessException("客户端凭证无效");
        }
        if (authorizationService != null) {
            OpenAppAuthorizationService.CredentialContext credential =
                    authorizationService.validateCredentialContext(clientId.trim(), clientSecret);
            if (credential != null) {
                OpenAppVO app = openAppService.selectVoById(credential.appId());
                if (app == null) {
                    throw new BusinessException("客户端所属应用不存在");
                }
                requireUsableApp(app, credential.environment());
                return new ClientAuthentication(credential.clientId(), credential.appId(),
                        credential.environment(), app);
            }
        }
        if (!openAppService.validateClient(clientId.trim(), clientSecret)) {
            throw new BusinessException("客户端凭证无效");
        }
        OpenAppVO app = requireEnabledApp(clientId.trim());
        requireUsableApp(app, "PROD");
        return new ClientAuthentication(app.getAppKey(), app.getAppId(), "PROD", app);
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
    private void requireGrantTypeAllowed(OpenAppVO app, String grantType) {
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

    private OpenAppVO resolveTokenApp(AccessTokenRecord record) {
        if (record.appId() != null) {
            OpenAppVO app = openAppService.selectVoById(record.appId());
            if (app != null) {
                return app;
            }
        }
        return requireEnabledApp(record.clientId());
    }

    private void requireUsableApp(OpenAppVO app, String environment) {
        if (app == null || app.getStatus() == null || app.getStatus() != STATUS_ENABLED) {
            throw new BusinessException("客户端不存在或已停用");
        }
        String normalized = normalizeEnvironment(environment);
        if (app.getVendorId() == null) {
            if (!"PROD".equals(normalized)) {
                throw new BusinessException("非厂商旧应用仅支持PROD环境");
            }
            return;
        }
        if (authorizationService == null) {
            throw new BusinessException("厂商状态校验未配置");
        }
        // 每次签发和校验都实时读取厂商状态，不能因 AccessToken 已在 Redis 中而绕过停用。
        authorizationService.requireActiveVendor(app.getVendorId(), app.getTenantId());
        Integer lifecycle = app.getLifecycleStatus();
        if (lifecycle == null || lifecycle == 6 || lifecycle == 7
                || ("SANDBOX".equals(normalized) && lifecycle < 2)
                || ("PROD".equals(normalized) && lifecycle != 5)) {
            throw new BusinessException("应用尚未开通" + normalized + "环境");
        }
    }

    private String normalizeEnvironment(String environment) {
        if (!StringUtils.hasText(environment)) {
            return "PROD";
        }
        String normalized = environment.trim().toUpperCase(java.util.Locale.ROOT);
        if (!Set.of("SANDBOX", "PROD").contains(normalized)) {
            throw new BusinessException("环境类型仅支持SANDBOX或PROD");
        }
        return normalized;
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
            Instant expiresAt,
            Long appId,
            String environment
    ) {
        private boolean isExpired() {
            return expiresAt != null && expiresAt.isBefore(Instant.now());
        }
    }

    private record RefreshTokenRecord(
            Long userId,
            String clientId,
            String scope,
            Instant expiresAt,
            Long appId,
            String environment
    ) {
        private boolean isExpired() {
            return expiresAt != null && expiresAt.isBefore(Instant.now());
        }
    }

    private record ClientAuthentication(String clientId, Long appId, String environment, OpenAppVO app) {
    }
}
