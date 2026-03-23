package com.han.common.core.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 服务间内部鉴权签名工具
 */
public final class InnerAuthSignUtil {

    private InnerAuthSignUtil() {
    }

    public static String sign(String client, String method, String path, long timestamp, String secret) {
        String payload = String.join("\n",
                safe(client),
                safe(method).toUpperCase(),
                safe(path),
                String.valueOf(timestamp),
                safe(secret)
        );
        return HanSecureUtil.sha256(payload);
    }

    public static boolean matches(String actual, String expected) {
        if (actual == null || expected == null) {
            return false;
        }
        return MessageDigest.isEqual(
                actual.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
