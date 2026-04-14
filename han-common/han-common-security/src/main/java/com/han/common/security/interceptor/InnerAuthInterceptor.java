package com.han.common.security.interceptor;

import com.han.common.core.config.InnerAuthProperties;
import com.han.common.core.constant.Constants;
import com.han.common.core.util.InnerAuthSignUtil;
import com.han.common.security.annotation.InnerAuth;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * @InnerAuth 服务端校验拦截器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InnerAuthInterceptor implements HandlerInterceptor {

    private final InnerAuthProperties properties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        if (!requiresInnerAuth(handlerMethod) || !properties.isEnabled()) {
            return true;
        }

        String client = request.getHeader(Constants.INNER_AUTH_CLIENT_HEADER);
        String timestampHeader = request.getHeader(Constants.INNER_AUTH_TIMESTAMP_HEADER);
        String signature = request.getHeader(Constants.INNER_AUTH_SIGNATURE_HEADER);

        if (isBlank(client) || isBlank(timestampHeader) || isBlank(signature)) {
            return reject(response, "内部调用鉴权头缺失");
        }

        long timestamp;
        try {
            timestamp = Long.parseLong(timestampHeader);
        } catch (NumberFormatException ex) {
            return reject(response, "内部调用时间戳非法");
        }

        long maxSkewMillis = properties.getClockSkewSeconds() * 1000L;
        if (Math.abs(System.currentTimeMillis() - timestamp) > maxSkewMillis) {
            return reject(response, "内部调用签名已过期");
        }

        String expected = InnerAuthSignUtil.sign(
                client,
                request.getMethod(),
                request.getRequestURI(),
                timestamp,
                properties.getSecret()
        );
        if (!InnerAuthSignUtil.matches(signature, expected)) {
            log.warn("内部鉴权签名校验失败: client={}, path={}", client, request.getRequestURI());
            return reject(response, "内部调用签名无效");
        }

        return true;
    }

    private boolean requiresInnerAuth(HandlerMethod handlerMethod) {
        return AnnotatedElementUtils.hasAnnotation(handlerMethod.getBeanType(), InnerAuth.class)
                || AnnotatedElementUtils.hasAnnotation(handlerMethod.getMethod(), InnerAuth.class);
    }

    private boolean reject(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"code\":403,\"msg\":\"" + message + "\"}");
        return false;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
