package com.han.common.security.filter;

import com.han.common.core.config.InnerAuthProperties;
import com.han.common.core.constant.CacheConstants;
import com.han.common.core.constant.Constants;
import com.han.common.core.util.HanSecureUtil;
import com.han.common.core.util.InnerAuthSignUtil;
import com.han.common.security.context.SecurityContextHolder;
import com.han.common.security.domain.LoginUser;
import com.han.common.security.interceptor.InnerAuthSignatureVerifier;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 服务侧登录态还原边界测试。
 *
 * <p>对应工单 S-12（无 Token 时信任裸 {@code X-User-Id} 构造登录态）与
 * S-13（{@code X-Tenant-Id} 无条件覆盖已解析出的租户上下文）。
 *
 * <p>同时锁定留给 core 组的内部调用身份契约：身份头必须由 v2 签名绑定才被采信。
 */
class HeaderAuthenticationFilterTest {

    private static final String TOKEN = "3f2a1c8e9b0d4f6a";
    private static final String SECRET = "unit-test-inner-secret";
    private static final String CLIENT = "han-auth";
    private static final String INNER_PATH = "/inner/system/user/7";
    private static final String SESSION_JSON =
            "{\"userId\":2,\"username\":\"alice\",\"tenantId\":5,\"permissions\":[\"system:user:list\"]}";

    private ValueOperations<String, String> valueOperations;
    private InnerAuthProperties innerAuthProperties;
    private HeaderAuthenticationFilter filter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);

        innerAuthProperties = new InnerAuthProperties();
        innerAuthProperties.setSecret(SECRET);
        filter = new HeaderAuthenticationFilter(redisTemplate, new InnerAuthSignatureVerifier(innerAuthProperties));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clear();
    }

    // ==================== Bearer 会话主路径 ====================

    @Test
    @DisplayName("合法 Bearer Token 且会话命中时建立完整登录态")
    void shouldBuildLoginUserFromSession() throws Exception {
        givenSession(TOKEN, SESSION_JSON);

        MockHttpServletRequest request = get("/system/user/list");
        request.addHeader(Constants.AUTHORIZATION_HEADER, Constants.TOKEN_PREFIX + TOKEN);

        LoginUser captured = runFilter(request);

        assertThat(captured).isNotNull();
        assertThat(captured.getUserId()).isEqualTo(2L);
        assertThat(captured.getUsername()).isEqualTo("alice");
        assertThat(captured.getTenantId()).isEqualTo(5L);
        assertThat(captured.isAdmin()).isFalse();
    }

    @Test
    @DisplayName("请求处理结束后清空线程上下文")
    void shouldClearContextAfterChain() throws Exception {
        givenSession(TOKEN, SESSION_JSON);

        MockHttpServletRequest request = get("/system/user/list");
        request.addHeader(Constants.AUTHORIZATION_HEADER, Constants.TOKEN_PREFIX + TOKEN);

        assertThat(runFilter(request)).isNotNull();
        assertThat(SecurityContextHolder.getLoginUser()).isNull();
    }

    // ==================== S-12 裸请求头不可信 ====================

    @Test
    @DisplayName("S-12：无 Token 只带 X-User-Id: 1 时不建立任何登录态")
    void shouldRejectBareUserIdHeader() throws Exception {
        MockHttpServletRequest request = get("/system/user/list");
        request.addHeader(Constants.USER_ID_HEADER, "1");
        request.addHeader(Constants.USERNAME_HEADER, "admin");

        assertThat(runFilter(request)).isNull();
    }

    @Test
    @DisplayName("S-12：Token 无效导致会话缺失时，X-User-Id 不能兜底成登录态")
    void shouldNotFallBackToHeaderWhenSessionMissing() throws Exception {
        MockHttpServletRequest request = get("/system/user/list");
        request.addHeader(Constants.AUTHORIZATION_HEADER, Constants.TOKEN_PREFIX + "expired-token");
        request.addHeader(Constants.USER_ID_HEADER, "1");

        assertThat(runFilter(request)).isNull();
    }

    @Test
    @DisplayName("S-12：/inner 路径上未带签名的身份头同样不可信")
    void shouldRejectUnsignedIdentityOnInnerPath() throws Exception {
        MockHttpServletRequest request = get(INNER_PATH);
        request.addHeader(Constants.USER_ID_HEADER, "1");
        request.addHeader(Constants.TENANT_ID_HEADER, "999");

        assertThat(runFilter(request)).isNull();
    }

    // ==================== S-13 租户上下文不可被请求头覆盖 ====================

    @Test
    @DisplayName("S-13：伪造 X-Tenant-Id 不能覆盖会话里的权威租户")
    void shouldIgnoreForgedTenantHeader() throws Exception {
        givenSession(TOKEN, SESSION_JSON);

        MockHttpServletRequest request = get("/system/user/list");
        request.addHeader(Constants.AUTHORIZATION_HEADER, Constants.TOKEN_PREFIX + TOKEN);
        request.addHeader(Constants.TENANT_ID_HEADER, "999");

        LoginUser captured = runFilter(request);

        assertThat(captured).isNotNull();
        assertThat(captured.getTenantId()).isEqualTo(5L);
    }

    // ==================== 内部调用身份契约（留给 core 组的约定） ====================

    @Test
    @DisplayName("内部调用：v2 签名绑定身份头时采信 userId 与 tenantId")
    void shouldTrustIdentityBoundByV2Signature() throws Exception {
        MockHttpServletRequest request = signedInnerRequest("7", "svc-caller", "5", true);

        LoginUser captured = runFilter(request);

        assertThat(captured).isNotNull();
        assertThat(captured.getUserId()).isEqualTo(7L);
        assertThat(captured.getUsername()).isEqualTo("svc-caller");
        assertThat(captured.getTenantId()).isEqualTo(5L);
        assertThat(captured.getPermissions()).isNull();
    }

    @Test
    @DisplayName("内部调用：v1 签名有效但未绑定身份头，身份不被采信")
    void shouldNotTrustIdentityUnderV1Signature() throws Exception {
        MockHttpServletRequest request = signedInnerRequest("7", "svc-caller", "5", false);

        assertThat(runFilter(request)).isNull();
    }

    @Test
    @DisplayName("内部调用：v2 签名后篡改 X-Tenant-Id 会导致校验失败并拒绝身份")
    void shouldRejectTamperedTenantUnderV2Signature() throws Exception {
        MockHttpServletRequest request = signedInnerRequest("7", "svc-caller", "5", true);
        request.removeHeader(Constants.TENANT_ID_HEADER);
        request.addHeader(Constants.TENANT_ID_HEADER, "999");

        assertThat(runFilter(request)).isNull();
    }

    @Test
    @DisplayName("内部调用：签名过期时拒绝身份")
    void shouldRejectExpiredSignature() throws Exception {
        long staleTimestamp = System.currentTimeMillis()
                - (innerAuthProperties.getClockSkewSeconds() + 60) * 1000L;
        MockHttpServletRequest request = signedInnerRequest("7", "svc-caller", "5", true, staleTimestamp);

        assertThat(runFilter(request)).isNull();
    }

    @Test
    @DisplayName("内部调用：inner-auth 关闭时不采信身份头（fail-closed）")
    void shouldRejectIdentityWhenInnerAuthDisabled() throws Exception {
        MockHttpServletRequest request = signedInnerRequest("7", "svc-caller", "5", true);
        innerAuthProperties.setEnabled(false);

        assertThat(runFilter(request)).isNull();
    }

    @Test
    @DisplayName("非 /inner 路径即使带 v2 签名也不采信身份头")
    void shouldRejectSignedIdentityOutsideInnerPath() throws Exception {
        MockHttpServletRequest request = signedRequest("/system/user/list", "1", "admin", "999", true,
                System.currentTimeMillis());

        assertThat(runFilter(request)).isNull();
    }

    // ==================== helpers ====================

    private void givenSession(String token, String json) {
        when(valueOperations.get(CacheConstants.TOKEN_KEY + token)).thenReturn(json);
    }

    private MockHttpServletRequest get(String uri) {
        return new MockHttpServletRequest("GET", uri);
    }

    private MockHttpServletRequest signedInnerRequest(String userId, String username, String tenantId, boolean v2) {
        return signedInnerRequest(userId, username, tenantId, v2, System.currentTimeMillis());
    }

    private MockHttpServletRequest signedInnerRequest(String userId, String username, String tenantId,
                                                      boolean v2, long timestamp) {
        return signedRequest(INNER_PATH, userId, username, tenantId, v2, timestamp);
    }

    private MockHttpServletRequest signedRequest(String uri, String userId, String username, String tenantId,
                                                 boolean v2, long timestamp) {
        MockHttpServletRequest request = get(uri);
        request.addHeader(Constants.USER_ID_HEADER, userId);
        request.addHeader(Constants.USERNAME_HEADER, username);
        request.addHeader(Constants.TENANT_ID_HEADER, tenantId);
        request.addHeader(Constants.INNER_AUTH_CLIENT_HEADER, CLIENT);
        request.addHeader(Constants.INNER_AUTH_TIMESTAMP_HEADER, String.valueOf(timestamp));

        String signature;
        if (v2) {
            request.addHeader(InnerAuthSignatureVerifier.SIGN_VERSION_HEADER,
                    InnerAuthSignatureVerifier.SIGN_VERSION_V2);
            signature = HanSecureUtil.sha256(String.join("\n",
                    CLIENT, "GET", uri, String.valueOf(timestamp), userId, username, tenantId, SECRET));
        } else {
            signature = InnerAuthSignUtil.sign(CLIENT, "GET", uri, timestamp, SECRET);
        }
        request.addHeader(Constants.INNER_AUTH_SIGNATURE_HEADER, signature);
        return request;
    }

    /**
     * 执行过滤器并捕获链路内部可见的登录态（过滤器会在 finally 中清空上下文）。
     */
    private LoginUser runFilter(MockHttpServletRequest request) throws Exception {
        AtomicReference<LoginUser> captured = new AtomicReference<>();
        FilterChain chain = (req, resp) -> captured.set(SecurityContextHolder.getLoginUser());

        filter.doFilter(request, new MockHttpServletResponse(), chain);
        return captured.get();
    }
}
