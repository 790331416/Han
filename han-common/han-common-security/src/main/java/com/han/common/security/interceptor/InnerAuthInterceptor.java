package com.han.common.security.interceptor;

import com.han.common.core.config.InnerAuthProperties;
import com.han.common.security.annotation.InnerAuth;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * @InnerAuth 服务端校验拦截器
 *
 * <p>签名判定委托给 {@link InnerAuthSignatureVerifier}，与 {@code HeaderAuthenticationFilter}
 * 的内部调用身份采信共用同一套口径。
 */
@Component
@RequiredArgsConstructor
public class InnerAuthInterceptor implements HandlerInterceptor {

    private final InnerAuthProperties properties;
    private final InnerAuthSignatureVerifier verifier;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        if (!requiresInnerAuth(handlerMethod) || !properties.isEnabled()) {
            return true;
        }

        return switch (verifier.verify(request)) {
            case OK -> true;
            case MISSING_HEADER -> reject(response, "内部调用鉴权头缺失");
            case BAD_TIMESTAMP -> reject(response, "内部调用时间戳非法");
            case EXPIRED -> reject(response, "内部调用签名已过期");
            case INVALID_SIGNATURE -> reject(response, "内部调用签名无效");
        };
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
}
