package com.han.open.util;

import com.han.common.core.exception.BusinessException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * PKCE（RFC 7636）校验工具。
 */
public final class PkceUtil {

    /** 推荐的挑战方法 */
    public static final String METHOD_S256 = "S256";

    /** RFC 7636 允许但不推荐的挑战方法 */
    public static final String METHOD_PLAIN = "plain";

    /** code_verifier 最小长度（RFC 7636 §4.1） */
    private static final int VERIFIER_MIN_LENGTH = 43;

    /** code_verifier 最大长度（RFC 7636 §4.1） */
    private static final int VERIFIER_MAX_LENGTH = 128;

    private PkceUtil() {
    }

    /**
     * 归一化 code_challenge_method。
     *
     * <p>RFC 7636 §4.3 规定缺省值为 {@code plain}；只接受 {@code plain} 与 {@code S256}。
     */
    public static String normalizeMethod(String method) {
        if (method == null || method.isBlank()) {
            return METHOD_PLAIN;
        }
        String trimmed = method.trim();
        if (METHOD_S256.equalsIgnoreCase(trimmed)) {
            return METHOD_S256;
        }
        if (METHOD_PLAIN.equalsIgnoreCase(trimmed)) {
            return METHOD_PLAIN;
        }
        throw new BusinessException("不支持的 code_challenge_method: " + method);
    }

    /**
     * 校验 code_verifier 的字符集与长度（RFC 7636 §4.1）。
     */
    public static boolean isValidVerifierFormat(String codeVerifier) {
        if (codeVerifier == null
                || codeVerifier.length() < VERIFIER_MIN_LENGTH
                || codeVerifier.length() > VERIFIER_MAX_LENGTH) {
            return false;
        }
        for (int i = 0; i < codeVerifier.length(); i++) {
            char c = codeVerifier.charAt(i);
            boolean unreserved = (c >= 'A' && c <= 'Z')
                    || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9')
                    || c == '-' || c == '.' || c == '_' || c == '~';
            if (!unreserved) {
                return false;
            }
        }
        return true;
    }

    /**
     * 比对 code_verifier 与授权阶段登记的 code_challenge。
     *
     * <p>S256 时比对 {@code BASE64URL(SHA256(ASCII(code_verifier)))}，plain 时直接比对原值，
     * 两者都走常量时间比较。
     */
    public static boolean matches(String codeVerifier, String codeChallenge, String method) {
        if (codeVerifier == null || codeChallenge == null) {
            return false;
        }
        if (!isValidVerifierFormat(codeVerifier)) {
            return false;
        }
        String expected = METHOD_S256.equals(method) ? deriveS256Challenge(codeVerifier) : codeVerifier;
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                codeChallenge.getBytes(StandardCharsets.US_ASCII));
    }

    /**
     * 计算 S256 挑战值。
     */
    public static String deriveS256Challenge(String codeVerifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", e);
        }
    }
}
