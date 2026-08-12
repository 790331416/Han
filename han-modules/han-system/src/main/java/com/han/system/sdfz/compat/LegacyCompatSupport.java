package com.han.system.sdfz.compat;

import com.han.common.core.util.HanJsonUtil;
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

    public Map<String, Object> handle(HttpServletRequest request, String path,
                                      Function<LegacyRequest, LegacyPayload> handler) {
        LegacyProtocol.Consumer consumer = LegacyProtocol.detectConsumer(request, path);
        String token = LegacyProtocol.token(request);
        if (!properties.isEnabled()) {
            return failure(consumer, token, "三课堂兼容层未启用");
        }
        try {
            LegacyRequest legacyRequest = new LegacyRequest(consumer, token, path, readParams(request, token));
            return success(consumer, token, handler.apply(legacyRequest));
        } catch (RuntimeException e) {
            log.warn("三课堂兼容接口处理失败: path={}, reason={}", path, e.getMessage());
            return failure(consumer, token, message(e));
        }
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
