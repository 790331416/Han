package com.han.common.core.util;

import com.fasterxml.jackson.core.type.TypeReference;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/** 最小化的三个课堂 HS256 兼容令牌编解码器。 */
public final class ClassroomTokenCodec {

    public static final String ISSUER = "han-classroom";
    public static final String SESSION_KEY_PREFIX = "sdfz:classroom:token:";
    public static final String EXCHANGE_KEY_PREFIX = "sdfz:classroom:exchange:";
    private static final String HEADER = "{\"alg\":\"HS256\",\"typ\":\"JWT\",\"kid\":\"han-classroom-v1\"}";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private ClassroomTokenCodec() {
    }

    public static String issue(Map<String, Object> sourceClaims, String secret, long issuedAt,
                               long ttlSeconds, String tokenId) {
        requireSecret(secret);
        if (ttlSeconds < 60 || ttlSeconds > 3600 || tokenId == null || tokenId.isBlank()) {
            throw new IllegalArgumentException("Invalid classroom token lifetime or id");
        }
        Map<String, Object> claims = new LinkedHashMap<>(sourceClaims);
        claims.put("iss", ISSUER);
        claims.put("iat", issuedAt);
        claims.put("exp", issuedAt + ttlSeconds);
        claims.put("jti", tokenId);
        String header = encode(HEADER.getBytes(StandardCharsets.UTF_8));
        String payload = encode(HanJsonUtil.toJsonString(claims).getBytes(StandardCharsets.UTF_8));
        String signingInput = header + "." + payload;
        return signingInput + "." + encode(sign(signingInput, secret));
    }

    public static VerifiedToken verify(String token, String secret, long nowEpochSecond) {
        requireSecret(secret);
        if (token == null || token.length() > 8192) {
            throw new IllegalArgumentException("Invalid classroom token");
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid classroom token");
        }
        Map<String, Object> header = decodeMap(parts[0]);
        if (!"HS256".equals(header.get("alg")) || !"JWT".equals(header.get("typ"))) {
            throw new IllegalArgumentException("Unsupported classroom token algorithm");
        }
        byte[] actualSignature = decode(parts[2]);
        byte[] expectedSignature = sign(parts[0] + "." + parts[1], secret);
        if (!MessageDigest.isEqual(actualSignature, expectedSignature)) {
            throw new IllegalArgumentException("Invalid classroom token signature");
        }

        Map<String, Object> claims = decodeMap(parts[1]);
        long issuedAt = longClaim(claims, "iat");
        long expiresAt = longClaim(claims, "exp");
        String tokenId = stringClaim(claims, "jti");
        if (!ISSUER.equals(claims.get("iss")) || issuedAt > nowEpochSecond + 60
                || expiresAt <= nowEpochSecond || expiresAt <= issuedAt || tokenId.isBlank()) {
            throw new IllegalArgumentException("Expired or invalid classroom token claims");
        }
        return new VerifiedToken(Map.copyOf(claims), tokenId, expiresAt);
    }

    public static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JDK missing SHA-256", e);
        }
    }

    private static byte[] sign(String value, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.US_ASCII));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to sign classroom token", e);
        }
    }

    private static Map<String, Object> decodeMap(String value) {
        try {
            return HanJsonUtil.getObjectMapper().readValue(decode(value), new TypeReference<>() { });
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid classroom token JSON", e);
        }
    }

    private static String encode(byte[] value) {
        return ENCODER.encodeToString(value);
    }

    private static byte[] decode(String value) {
        try {
            return DECODER.decode(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid classroom token encoding", e);
        }
    }

    private static long longClaim(Map<String, Object> claims, String name) {
        Object value = claims.get(name);
        if (value instanceof Number number) return number.longValue();
        throw new IllegalArgumentException("Missing classroom token claim: " + name);
    }

    private static String stringClaim(Map<String, Object> claims, String name) {
        Object value = claims.get(name);
        return value instanceof String text ? text : "";
    }

    private static void requireSecret(String secret) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("Classroom token secret must contain at least 32 bytes");
        }
    }

    public record VerifiedToken(Map<String, Object> claims, String tokenId, long expiresAt) {
    }
}
