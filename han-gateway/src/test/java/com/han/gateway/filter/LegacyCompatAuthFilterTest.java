package com.han.gateway.filter;

import com.han.common.core.util.ClassroomTokenCodec;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LegacyCompatAuthFilterTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    void letsThroughDirectoryCallsCarryingAValidCompatToken() {
        String token = issue("jti-ok");
        ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
        when(redis.hasKey(ClassroomTokenCodec.SESSION_KEY_PREFIX + "jti-ok")).thenReturn(Mono.just(true));
        MockServerWebExchange exchange = request(
                "/sdfz-compat/manager/teacher/getTeacherInfoList", token);
        AtomicBoolean forwarded = new AtomicBoolean();

        new LegacyCompatAuthFilter(redis, SECRET).filter(exchange, ignored -> {
            forwarded.set(true);
            return Mono.empty();
        }).block();

        assertThat(forwarded).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void rejectsDirectoryCallsWithoutAnyToken() {
        MockServerWebExchange exchange = request("/sdfz-compat/manager/teacher/getTeacherInfoList", null);
        AtomicBoolean forwarded = new AtomicBoolean();

        new LegacyCompatAuthFilter(mock(ReactiveStringRedisTemplate.class), SECRET)
                .filter(exchange, ignored -> {
                    forwarded.set(true);
                    return Mono.empty();
                }).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(forwarded).isFalse();
    }

    @Test
    void rejectsForgedAndRevokedTokens() {
        MockServerWebExchange forged = request(
                "/sdfz-compat/user/identity/getIdentityBypkId", issue("jti-x") + "tampered");
        new LegacyCompatAuthFilter(mock(ReactiveStringRedisTemplate.class), SECRET)
                .filter(forged, ignored -> Mono.empty()).block();
        assertThat(forged.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
        when(redis.hasKey(ClassroomTokenCodec.SESSION_KEY_PREFIX + "jti-revoked")).thenReturn(Mono.just(false));
        MockServerWebExchange revoked = request(
                "/sdfz-compat/user/identity/getIdentityBypkId", issue("jti-revoked"));
        new LegacyCompatAuthFilter(redis, SECRET).filter(revoked, ignored -> Mono.empty()).block();
        assertThat(revoked.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void allowsOnlyTheEnumeratedPreLoginPathsWithoutAToken() {
        List<String> anonymous = List.of(
                "user/sys/randomImage/1700000000000",
                "user/user/login",
                "user/user/loginByCaptcha",
                "user/public/login/get-sms-code",
                "user/user/user-forget-password",
                "partner/tPartnerUserLogin/userVoByJyyToken");

        assertThat(anonymous).allSatisfy(path -> assertThat(LegacyCompatAuthFilter.isAnonymous(path)).isTrue());
    }

    @Test
    void keepsEveryDirectoryAndPostLoginPathBehindAuthentication() {
        List<String> guarded = List.of(
                "user/userInfo/getById",
                "user/userInfo/getUserInfo",
                "user/identity/getIdentityBypkId",
                "user/org/getOrgChildList",
                "user/org/getById",
                "user/org/org-list-by-page",
                "user/org/getSchoolInfo",
                "manager/org/getOrgInfoForExternal",
                "manager/org/get-lazy-org-tree",
                "manager/org-branch/get-org-branch-tree",
                "manager/pinyin/get-org-result-by-areaCode",
                "manager/teacher/getTeacherInfoList",
                "configuration/school/place/selectPlace",
                "device/sysDevice/getDeviceList",
                "device/sysDevice/getDeviceInfoByDeviceCode",
                "user/user/getOneById",
                "user/sys/dict/getDictItems/course_status",
                "sidecar/fileview/authorizationCode");

        assertThat(guarded).hasSize(18)
                .allSatisfy(path -> assertThat(LegacyCompatAuthFilter.isAnonymous(path)).isFalse());
    }

    @Test
    void doesNotLetANestedPathSneakPastTheCaptchaPrefix() {
        assertThat(LegacyCompatAuthFilter.isAnonymous("user/sys/randomImageX/1")).isFalse();
        assertThat(LegacyCompatAuthFilter.isAnonymous("user/user/login/extra")).isFalse();
    }

    @Test
    void closesTheWholePrefixWhenNoSigningSecretIsConfigured() {
        MockServerWebExchange exchange = request("/sdfz-compat/user/user/login", null);

        new LegacyCompatAuthFilter(mock(ReactiveStringRedisTemplate.class), "")
                .filter(exchange, ignored -> Mono.empty()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void ignoresRequestsOutsideTheCompatPrefix() {
        MockServerWebExchange exchange = request("/system/education/people/list", null);
        AtomicBoolean forwarded = new AtomicBoolean();

        new LegacyCompatAuthFilter(mock(ReactiveStringRedisTemplate.class), SECRET)
                .filter(exchange, ignored -> {
                    forwarded.set(true);
                    return Mono.empty();
                }).block();

        assertThat(forwarded).isTrue();
    }

    private static MockServerWebExchange request(String path, String token) {
        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.get(path);
        if (token != null) {
            builder.header("access-token", token);
        }
        return MockServerWebExchange.from(builder.build());
    }

    private static String issue(String tokenId) {
        return ClassroomTokenCodec.issue(Map.of("userId", "100", "userType", "USER"),
                SECRET, Instant.now().getEpochSecond(), 3600, tokenId);
    }
}
