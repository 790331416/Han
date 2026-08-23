package com.han.open.config;

import com.han.common.core.config.InnerAuthProperties;
import com.han.common.core.constant.Constants;
import com.han.common.core.util.InnerAuthSignUtil;
import com.han.common.security.annotation.InnerAuth;
import com.han.common.security.interceptor.InnerAuthInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 内部服务互信签名校验的负例行为测试。
 *
 * <p>han-open 的内部鉴权校验逻辑位于公共模块
 * {@link InnerAuthInterceptor}（配合 {@link InnerAuthSignUtil}），
 * 不依赖 Spring 容器即可直接构造，因此直接以拦截器 preHandle 为核心校验层
 * 进行单测：构造错误 / 缺失签名输入，断言其拒绝（返回 false 并写 403）。
 */
class HanOpenInnerAuthSignatureTest {

    private final InnerAuthProperties properties = new InnerAuthProperties();
    private final InnerAuthInterceptor interceptor = new InnerAuthInterceptor(properties);
    private HandlerMethod handler;

    @BeforeEach
    void setUp() throws Exception {
        properties.setEnabled(true);
        properties.setSecret("test-secret");
        properties.setClockSkewSeconds(300);
        handler = new HandlerMethod(
                new InnerOnlyEndpoint(),
                InnerOnlyEndpoint.class.getDeclaredMethod("probe"));
    }

    @Test
    void rejectsWhenInnerAuthHeadersMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/inner/only");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, handler);

        assertThat(result).isFalse();
        assertThat(response.getStatus()).isEqualTo(Constants.FORBIDDEN);
        assertThat(response.getContentAsString()).contains("内部调用鉴权头缺失");
    }

    @Test
    void rejectsWhenSignatureInvalid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/inner/only");
        request.addHeader(Constants.INNER_AUTH_CLIENT_HEADER, "sys");
        request.addHeader(Constants.INNER_AUTH_TIMESTAMP_HEADER, String.valueOf(System.currentTimeMillis()));
        request.addHeader(Constants.INNER_AUTH_SIGNATURE_HEADER, "wrong-signature");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, handler);

        assertThat(result).isFalse();
        assertThat(response.getStatus()).isEqualTo(Constants.FORBIDDEN);
        assertThat(response.getContentAsString()).contains("内部调用签名无效");
    }

    @Test
    void rejectsWhenTimestampExpired() throws Exception {
        long staleTimestamp = System.currentTimeMillis() - 400_000L;
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/inner/only");
        request.addHeader(Constants.INNER_AUTH_CLIENT_HEADER, "sys");
        request.addHeader(Constants.INNER_AUTH_TIMESTAMP_HEADER, String.valueOf(staleTimestamp));
        request.addHeader(Constants.INNER_AUTH_SIGNATURE_HEADER, "irrelevant");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, handler);

        assertThat(result).isFalse();
        assertThat(response.getStatus()).isEqualTo(Constants.FORBIDDEN);
        assertThat(response.getContentAsString()).contains("内部调用签名已过期");
    }

    @Test
    void acceptsWhenSignatureValid() throws Exception {
        long timestamp = System.currentTimeMillis();
        String signature = InnerAuthSignUtil.sign(
                "sys", "GET", "/inner/only", timestamp, properties.getSecret());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/inner/only");
        request.addHeader(Constants.INNER_AUTH_CLIENT_HEADER, "sys");
        request.addHeader(Constants.INNER_AUTH_TIMESTAMP_HEADER, String.valueOf(timestamp));
        request.addHeader(Constants.INNER_AUTH_SIGNATURE_HEADER, signature);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, handler);

        assertThat(result).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @InnerAuth
    static class InnerOnlyEndpoint {
        public String probe() {
            return "ok";
        }
    }
}
