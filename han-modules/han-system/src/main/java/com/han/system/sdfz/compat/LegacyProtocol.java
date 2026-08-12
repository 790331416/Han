package com.han.system.sdfz.compat;

import com.han.common.core.util.HanJsonUtil;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 旧三课堂调用信封的解析规则。
 *
 * <p>两个消费者的发包方式不同，本类负责把差异收敛掉：
 * 旧 api 用 hutool 发 {@code GET} 但把 {@code param} 放在 {@code x-www-form-urlencoded} 请求体里，
 * 旧前端则把 {@code param} 放在查询串或表单上；两者解密后期望的响应形态也相反。
 */
public final class LegacyProtocol {

    public static final String PARAM = "param";
    public static final String TOKEN_HEADER = "access-token";
    public static final String PLATFORM_HEADER = "x-platform";
    /** 旧前端固定发空串，旧 api 完全不发，可作为消费者判别的首选信号。 */
    public static final String CHECK_CODE_HEADER = "check-code";

    /** 请求体读取上限，兼容层只接收查询串规模的 param，超出即视为异常流量。 */
    private static final int MAX_BODY_BYTES = 64 * 1024;

    /** 旧 api {@code CommonService} 的 15 个路径，用于在无其它信号时判定消费者。 */
    private static final Set<String> LEGACY_API_PATHS = Set.of(
            LegacyPaths.USER_INFO_GET_BY_ID,
            LegacyPaths.USER_INFO_GET_USER_INFO,
            LegacyPaths.IDENTITY_GET_BY_PK_ID,
            LegacyPaths.ORG_CHILD_LIST,
            LegacyPaths.ORG_GET_BY_ID,
            LegacyPaths.ORG_LIST_BY_PAGE,
            LegacyPaths.ORG_SCHOOL_INFO,
            LegacyPaths.MANAGER_ORG_INFO_FOR_EXTERNAL,
            LegacyPaths.MANAGER_LAZY_ORG_TREE,
            LegacyPaths.MANAGER_ORG_BRANCH_TREE,
            LegacyPaths.PINYIN_ORG_RESULT,
            LegacyPaths.MANAGER_TEACHER_LIST,
            LegacyPaths.SELECT_PLACE,
            LegacyPaths.DEVICE_LIST,
            LegacyPaths.DEVICE_BY_CODE);

    private LegacyProtocol() {
    }

    /** 解密后期望的响应形态：旧 api 要裸对象，旧前端要完整信封。 */
    public enum Consumer {
        LEGACY_API,
        LEGACY_UI
    }

    public static Consumer detectConsumer(HttpServletRequest request, String canonicalPath) {
        if (request.getHeader(CHECK_CODE_HEADER) != null) {
            return Consumer.LEGACY_UI;
        }
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return Consumer.LEGACY_UI;
        }
        return LEGACY_API_PATHS.contains(canonicalPath) ? Consumer.LEGACY_API : Consumer.LEGACY_UI;
    }

    public static String token(HttpServletRequest request) {
        String token = request.getHeader(TOKEN_HEADER);
        if (token == null || token.isBlank()) {
            token = request.getParameter(TOKEN_HEADER);
        }
        return token == null || token.isBlank() ? null : token.trim();
    }

    /**
     * 按「请求体 → 查询串 → 表单」依次取 {@code param}。
     *
     * <p>先读体覆盖旧 api 的 GET 带 body；体为空时 {@code getParameter} 同时覆盖查询串与 POST 表单。
     */
    public static String extractParam(HttpServletRequest request) {
        String fromBody = fromBody(request);
        if (fromBody != null) {
            return fromBody;
        }
        String fromQueryOrForm = request.getParameter(PARAM);
        return fromQueryOrForm == null || fromQueryOrForm.isBlank() ? null : fromQueryOrForm.trim();
    }

    /** 解析 {@code k1=v1&k2=v2} 形式的明文参数串，重复键取首次出现的值。 */
    public static Map<String, String> parseQueryString(String plaintext) {
        Map<String, String> params = new LinkedHashMap<>();
        if (plaintext == null || plaintext.isBlank()) {
            return params;
        }
        for (String pair : plaintext.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int separator = pair.indexOf('=');
            String name = separator < 0 ? pair : pair.substring(0, separator);
            String value = separator < 0 ? "" : pair.substring(separator + 1);
            String key = decode(name).trim();
            if (!key.isEmpty()) {
                params.putIfAbsent(key, decode(value));
            }
        }
        return params;
    }

    private static String fromBody(HttpServletRequest request) {
        String body = readBody(request);
        if (body == null || body.isBlank()) {
            return null;
        }
        String trimmed = body.trim();
        if (trimmed.startsWith("{")) {
            Object value = HanJsonUtil.parseMap(trimmed).get(PARAM);
            return value instanceof String text && !text.isBlank() ? text.trim() : null;
        }
        String value = parseQueryString(trimmed).get(PARAM);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String readBody(HttpServletRequest request) {
        long declared = request.getContentLengthLong();
        if (declared == 0) {
            return null;
        }
        try (InputStream input = request.getInputStream()) {
            if (input == null) {
                return null;
            }
            byte[] bytes = input.readNBytes(MAX_BODY_BYTES);
            return bytes.length == 0 ? null : new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException | IllegalStateException e) {
            return null;
        }
    }

    /**
     * 仅在值里出现 {@code %} 时才做百分号解码。
     *
     * <p>旧 api 是裸拼接查询串，无条件解码会把合法的 {@code +} 变成空格。
     */
    private static String decode(String value) {
        if (value.indexOf('%') < 0) {
            return value;
        }
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return value;
        }
    }
}
