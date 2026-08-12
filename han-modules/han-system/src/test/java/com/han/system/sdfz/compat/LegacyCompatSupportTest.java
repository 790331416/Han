package com.han.system.sdfz.compat;

import com.han.common.core.exception.BusinessException;
import com.han.common.core.util.ClassroomAesCodec;
import com.han.common.core.util.ClassroomTokenCodec;
import com.han.common.core.util.HanJsonUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyCompatSupportTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private static final String ANON_KEY = "0123456789ABCDEF";
    private static final String ANON_IV = "FEDCBA9876543210";

    private LegacyCompatProperties properties;
    private LegacyCipher cipher;
    private LegacyCompatSupport support;

    @BeforeEach
    void setUp() {
        properties = new LegacyCompatProperties();
        properties.setEnabled(true);
        properties.setTokenSecret(SECRET);
        properties.setAnonymousKey(ANON_KEY);
        properties.setAnonymousIv(ANON_IV);
        cipher = new LegacyCipher(properties);
        support = new LegacyCompatSupport(properties, cipher);
    }

    // ------------------------------------------------------------ 两种输出形态

    @Test
    void legacyApiGetsTheBareObjectSoToBeanCanMapItDirectly() {
        MockHttpServletRequest request = get(LegacyPaths.IDENTITY_GET_BY_PK_ID);

        Map<String, Object> body = support.handle(request, LegacyPaths.IDENTITY_GET_BY_PK_ID,
                ignored -> LegacyPayload.same(Map.of("orgId", "7", "orgName", "附中")));

        assertThat(body).containsEntry("code", 2000);
        Map<String, Object> plaintext = decryptToMap(body, null);
        assertThat(plaintext)
                .containsEntry("orgId", "7")
                .doesNotContainKey("result");
    }

    @Test
    void legacyFrontendGetsAFullEnvelopeBecauseItReplacesTheWholeResponse() {
        MockHttpServletRequest request = post(LegacyPaths.UI_GET_ONE_BY_ID);

        Map<String, Object> body = support.handle(request, LegacyPaths.UI_GET_ONE_BY_ID,
                ignored -> LegacyPayload.same(Map.of("accessToken", "t")));

        Map<String, Object> plaintext = decryptToMap(body, null);
        assertThat(plaintext)
                .containsEntry("code", 200)
                .containsEntry("success", true);
        assertThat(asMap(plaintext.get("result"))).containsEntry("accessToken", "t");
    }

    /**
     * 通道 B 上多包一层信封是本次接口收敛引入的唯一新风险：
     * {@code /common/*} 会再包一层，前端读到 {@code res.result.result.*}，下拉框全空且页面不报错。
     */
    @Test
    void channelBNeverWrapsTheBusinessObjectInAnEnvelope() {
        LegacyPayload single = LegacyPayload.same(Map.of("deviceCode", "DEV-1"));

        Map<String, Object> plaintext = decryptToMap(support.handle(
                get(LegacyPaths.DEVICE_BY_CODE), LegacyPaths.DEVICE_BY_CODE, ignored -> single), null);

        assertThat(plaintext)
                .containsEntry("deviceCode", "DEV-1")
                .doesNotContainKeys("result", "success", "message");
        assertThat(plaintext.get("code")).isNull();
    }

    @Test
    void channelBListsDecryptToABareArrayNotAnEnvelope() {
        LegacyPayload list = LegacyPayload.list(List.of(Map.of("deviceCode", "DEV-1")));

        String ciphertext = (String) support.handle(
                get(LegacyPaths.DEVICE_LIST), LegacyPaths.DEVICE_LIST, ignored -> list).get("result");
        String plaintext = ClassroomAesCodec.decrypt(ciphertext, ANON_KEY, ANON_IV);

        assertThat(plaintext).startsWith("[").contains("\"deviceCode\":\"DEV-1\"");
    }

    @Test
    void channelBPagedResultsDecryptToRecordsAndTotalAtTheTopLevel() {
        LegacyPayload page = LegacyPayload.page(List.of(Map.of("userId", "100")), 1, 1, 20);

        Map<String, Object> plaintext = decryptToMap(support.handle(
                get(LegacyPaths.MANAGER_TEACHER_LIST), LegacyPaths.MANAGER_TEACHER_LIST,
                ignored -> page), null);

        assertThat(plaintext).containsKeys("records", "total").doesNotContainKey("result");
    }

    @Test
    void captchaKeepsTheZeroCodeInsideTheFrontendEnvelope() {
        Map<String, Object> body = support.handle(get(LegacyPaths.UI_RANDOM_IMAGE), LegacyPaths.UI_RANDOM_IMAGE,
                ignored -> LegacyPayload.same("data:image/png;base64,xxx")
                        .withUiCode(LegacyPayload.UI_CAPTCHA_OK));

        assertThat(decryptToMap(body, null)).containsEntry("code", 0);
    }

    // ------------------------------------------------------------ 参数解密

    @Test
    void decryptsParamWithTheTokenDerivedKeyWhenTheRequestIsAuthenticated() {
        String token = ClassroomTokenCodec.issue(Map.of("userId", "100"), SECRET,
                Instant.now().getEpochSecond(), 3600, "jti-1");
        MockHttpServletRequest request = get(LegacyPaths.IDENTITY_GET_BY_PK_ID);
        request.addHeader(LegacyProtocol.TOKEN_HEADER, token);
        request.setContent(("param=" + ClassroomAesCodec.encryptWithToken("pkId=11", token))
                .getBytes(StandardCharsets.UTF_8));

        Map<String, Object> body = support.handle(request, LegacyPaths.IDENTITY_GET_BY_PK_ID,
                legacyRequest -> LegacyPayload.same(Map.of("seen", legacyRequest.text("pkId"))));

        assertThat(decryptToMap(body, token)).containsEntry("seen", "11");
    }

    @Test
    void decryptsParamWithTheAnonymousKeyBeforeLogin() {
        MockHttpServletRequest request = post(LegacyPaths.UI_LOGIN);
        request.setContent(("param=" + ClassroomAesCodec.encrypt("phone=teacher01", ANON_KEY, ANON_IV))
                .getBytes(StandardCharsets.UTF_8));

        Map<String, Object> body = support.handle(request, LegacyPaths.UI_LOGIN,
                legacyRequest -> LegacyPayload.same(Map.of("seen", legacyRequest.text("phone"))));

        assertThat(asMap(decryptToMap(body, null).get("result"))).containsEntry("seen", "teacher01");
    }

    @Test
    void reportsUndecryptableParamsAsABusinessFailureRatherThanAnException() {
        MockHttpServletRequest request = get(LegacyPaths.IDENTITY_GET_BY_PK_ID);
        request.setContent("param=zzzz".getBytes(StandardCharsets.UTF_8));

        Map<String, Object> body = support.handle(request, LegacyPaths.IDENTITY_GET_BY_PK_ID,
                ignored -> LegacyPayload.same(Map.of()));

        assertThat(body).containsEntry("code", 500);
    }

    // ------------------------------------------------------------ 错误路径

    @Test
    void alwaysEmitsACodeFieldBecauseTheLegacyApiUnboxesItIntoAnInteger() {
        Map<String, Object> failure = support.handle(get(LegacyPaths.IDENTITY_GET_BY_PK_ID),
                LegacyPaths.IDENTITY_GET_BY_PK_ID,
                ignored -> {
                    throw new BusinessException("boom");
                });

        assertThat(failure.get("code")).isInstanceOf(Integer.class);
    }

    @Test
    void neverAnswersWithUnauthorizedBecauseTheFrontendWouldRedirectToProduction() {
        Map<String, Object> failure = support.handle(post(LegacyPaths.UI_GET_ONE_BY_ID),
                LegacyPaths.UI_GET_ONE_BY_ID,
                ignored -> {
                    throw new BusinessException("登录状态已失效，请重新登录");
                });

        assertThat(decryptToMap(failure, null))
                .containsEntry("code", 500)
                .containsEntry("message", "登录状态已失效，请重新登录");
    }

    @Test
    void failsClosedWhenTheCompatLayerIsDisabled() {
        properties.setEnabled(false);

        Map<String, Object> body = support.handle(get(LegacyPaths.DEVICE_LIST), LegacyPaths.DEVICE_LIST,
                ignored -> LegacyPayload.same(Map.of()));

        assertThat(body).containsEntry("code", 500);
    }

    @Test
    void degradesToAPlainFailureWhenNoAesKeyIsConfigured() {
        properties.setAnonymousKey("");

        Map<String, Object> body = support.handle(post(LegacyPaths.UI_LOGIN), LegacyPaths.UI_LOGIN,
                ignored -> LegacyPayload.same(Map.of()));

        assertThat(body).containsEntry("code", 500);
        assertThat(body.get("result")).isInstanceOf(Map.class);
    }

    @Test
    void mountsDisabledEndpointsWithAReadableEnvelopeInsteadOfA404() {
        Map<String, Object> body = support.unavailable(post(LegacyPaths.UI_SMS_CODE),
                LegacyPaths.UI_SMS_CODE, "短信验证码本期未启用");

        assertThat(decryptToMap(body, null))
                .containsEntry("code", 500)
                .containsEntry("message", "短信验证码本期未启用");
    }

    // ------------------------------------------------------------ 夹具

    private static MockHttpServletRequest get(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", LegacyPaths.ROOT + "/" + path);
        request.setContentType("application/x-www-form-urlencoded");
        return request;
    }

    private static MockHttpServletRequest post(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", LegacyPaths.ROOT + "/" + path);
        request.setContentType("application/x-www-form-urlencoded");
        request.addHeader(LegacyProtocol.CHECK_CODE_HEADER, "");
        return request;
    }

    private Map<String, Object> decryptToMap(Map<String, Object> body, String token) {
        String ciphertext = (String) body.get("result");
        String plaintext = token != null
                ? ClassroomAesCodec.decryptWithToken(ciphertext, token)
                : ClassroomAesCodec.decrypt(ciphertext, ANON_KEY, ANON_IV);
        return HanJsonUtil.parseMap(plaintext);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }
}
