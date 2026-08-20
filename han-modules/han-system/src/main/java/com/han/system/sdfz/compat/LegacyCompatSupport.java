package com.han.system.sdfz.compat;

import com.han.common.core.util.HanJsonUtil;
import com.han.common.core.util.ClassroomTokenCodec;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * 兼容调用的信封拆装与错误兜底。
 *
 * <p>响应恒为 HTTP 200，业务结果放在体内：旧 api 用 {@code 2000 == code} 对 {@code Integer} 拆箱，
 * 响应里少 {@code code} 会直接 NPE；旧前端把 401 当成登录过期并跳转到生产域名。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LegacyCompatSupport {

    /** 旧侧约定：{@code code == 2000} 表示 {@code result} 是密文。 */
    private static final int ENCRYPTED = 2000;
    private static final int FAILURE = 500;

    private final LegacyCompatProperties properties;
    private final LegacyCipher cipher;
    private final LegacyTokenIssuer tokenIssuer;

    public Map<String, Object> handle(HttpServletRequest request, String path,
                                      Function<LegacyRequest, LegacyPayload> handler) {
        return handle(request, path, false, handler);
    }

    /** 旧校端经 CommonService 调用的目录接口：必须先验签，再把令牌学校范围交给查询层。 */
    public Map<String, Object> handleDirectory(HttpServletRequest request, String path,
                                               Function<LegacyRequest, LegacyPayload> handler) {
        return handle(request, path, true, handler);
    }

    private Map<String, Object> handle(HttpServletRequest request, String path, boolean directory,
                                       Function<LegacyRequest, LegacyPayload> handler) {
        LegacyProtocol.Consumer consumer = LegacyProtocol.detectConsumer(request, path);
        String token = LegacyProtocol.token(request);
        if (!properties.isEnabled()) {
            return failure(consumer, token, "三课堂兼容层未启用");
        }
        try {
            LegacyRequest.Scope scope = directory ? verifiedScope(token) : null;
            LegacyRequest legacyRequest = new LegacyRequest(consumer, token, path, readParams(request, token), scope);
            return success(consumer, token, handler.apply(legacyRequest));
        } catch (RuntimeException e) {
            log.warn("三课堂兼容接口处理失败: path={}, reason={}", path, e.getMessage());
            return failure(consumer, token, message(e));
        }
    }

    /**
     * 目录不能只把 token 当 AES 密钥：签名、Redis 会话、租户和学校范围必须全部可用。
     * 学校范围只来自已签凭证，绝不从 orgId、areaCode 等请求参数推导。
     */
    private LegacyRequest.Scope verifiedScope(String token) {
        if (token == null) {
            throw new IllegalArgumentException("登录状态已失效，请重新登录");
        }
        ClassroomTokenCodec.VerifiedToken verified = tokenIssuer.verify(token);
        long tenantId = claimAsPositiveLong(verified, "tenantId");
        long schoolId = claimAsPositiveLong(verified, "schoolId");
        long identityId = claimAsPositiveLong(verified, "identityId");
        long userId = claimAsPositiveLong(verified, "hanUserId");
        if (tenantId != properties.getTenantId()) {
            throw new IllegalArgumentException("登录状态已失效，请重新登录");
        }
        return new LegacyRequest.Scope(tenantId, schoolId, identityId, userId);
    }

    private static long claimAsPositiveLong(ClassroomTokenCodec.VerifiedToken verified, String name) {
        Object value = verified.claims().get(name);
        try {
            long parsed = value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
            if (parsed > 0) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
            // 统一按登录状态失效返回，避免把内部 claim 结构暴露给旧调用方。
        }
        throw new IllegalArgumentException("登录状态已失效，请重新登录");
    }

    /** 本期未启用的旧接口：仍按旧信封应答，避免前端拿到 404 或 HTML 错误页。 */
    public Map<String, Object> unavailable(HttpServletRequest request, String path, String message) {
        LegacyProtocol.Consumer consumer = LegacyProtocol.detectConsumer(request, path);
        return failure(consumer, LegacyProtocol.token(request), message);
    }

    private Map<String, String> readParams(HttpServletRequest request, String token) {
        String ciphertext = LegacyProtocol.extractParam(request);
        if (ciphertext == null) {
            return Map.of();
        }
        try {
            return LegacyProtocol.parseQueryString(cipher.decrypt(ciphertext, token));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("请求参数解密失败", e);
        }
    }

    /**
     * 通道 B 只给裸业务对象，通道 C 给完整信封。
     *
     * <p>收敛后目录流量都经由旧 api 的 {@code /common/*} 转发，旧 api 解开 {@code result} 后会再包一层；
     * 这里要是多包了一层信封，前端读到的就是 {@code res.result.result.*}，
     * 现象是下拉框全空且页面不报错，极难定位。
     */
    private Map<String, Object> success(LegacyProtocol.Consumer consumer, String token, LegacyPayload payload) {
        Object plaintext = consumer == LegacyProtocol.Consumer.LEGACY_API
                ? payload.value()
                : envelope(payload.uiCode(), true, "", payload.value());
        return encrypted(token, plaintext, consumer, "");
    }

    private Map<String, Object> failure(LegacyProtocol.Consumer consumer, String token, String message) {
        if (consumer == LegacyProtocol.Consumer.LEGACY_API) {
            // 旧 api 在 code != 2000 时把整个响应体当作 result 交给 toBean，字段对不上会得到全 null 对象而非异常。
            return envelope(FAILURE, false, message, Map.of());
        }
        return encrypted(token, envelope(FAILURE, false, message, Map.of()), consumer, message);
    }

    /**
     * 组装 {@code {code:2000, result:<密文>}}。
     *
     * <p>加密所需的密钥来自本次请求的凭证或配置里的匿名密钥；两者都不可用时降级成明文失败信封，
     * 让调用方看到可读的错误而不是一个解不开的密文。
     */
    private Map<String, Object> encrypted(String token, Object plaintext,
                                          LegacyProtocol.Consumer consumer, String fallbackMessage) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("code", ENCRYPTED);
            body.put("success", true);
            body.put("message", "");
            body.put("result", cipher.encrypt(HanJsonUtil.toJsonString(plaintext), token));
            return body;
        } catch (RuntimeException e) {
            log.warn("三课堂兼容响应加密失败: consumer={}, reason={}", consumer, e.getMessage());
            String message = fallbackMessage == null || fallbackMessage.isBlank()
                    ? "兼容响应加密失败" : fallbackMessage;
            return envelope(FAILURE, false, message, Map.of());
        }
    }

    private static Map<String, Object> envelope(int code, boolean success, String message, Object result) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("code", code);
        value.put("success", success);
        value.put("message", message);
        value.put("result", result);
        return value;
    }

    private static String message(RuntimeException e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? "三课堂兼容接口处理失败" : message;
    }
}
