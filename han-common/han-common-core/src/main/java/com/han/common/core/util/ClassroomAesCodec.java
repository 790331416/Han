package com.han.common.core.util;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * 三个课堂旧协议的 AES 编解码器：AES-128-CBC、PKCS5 填充、密文小写 hex。
 *
 * <p>旧前端 {@code utils/aes.ts}、旧网关 {@code AesUtil} 与旧 api {@code AesUtil} 三处算法一致：
 * 已登录请求用凭证派生密钥（{@code key = token[len-48, len-32)}、{@code iv = token[len-16, len)}），
 * 未登录请求用客户端内置的匿名密钥——匿名密钥属于对端契约，由调用方从配置传入，不在本类内置。
 */
public final class ClassroomAesCodec {

    /** 凭证派生密钥所需的最短长度，短于此值 substring 会越界。 */
    public static final int MIN_TOKEN_LENGTH = 48;

    private static final String TRANSFORMATION = "AES/CBC/PKCS5Padding";
    private static final int AES_128_BYTES = 16;

    private ClassroomAesCodec() {
    }

    /** 凭证是否可用于派生密钥：长度足够且末 48 位全为单字节 ASCII。 */
    public static boolean canDeriveKey(String token) {
        if (token == null || token.length() < MIN_TOKEN_LENGTH) {
            return false;
        }
        String tail = token.substring(token.length() - MIN_TOKEN_LENGTH);
        for (int index = 0; index < tail.length(); index++) {
            if (tail.charAt(index) > 0x7F) {
                return false;
            }
        }
        return true;
    }

    public static String deriveKey(String token) {
        requireDerivable(token);
        return token.substring(token.length() - 48, token.length() - 32);
    }

    public static String deriveIv(String token) {
        requireDerivable(token);
        return token.substring(token.length() - 16);
    }

    public static String encryptWithToken(String plaintext, String token) {
        return encrypt(plaintext, deriveKey(token), deriveIv(token));
    }

    public static String decryptWithToken(String ciphertextHex, String token) {
        return decrypt(ciphertextHex, deriveKey(token), deriveIv(token));
    }

    public static String encrypt(String plaintext, String key, String iv) {
        if (plaintext == null) {
            throw new IllegalArgumentException("Classroom plaintext is required");
        }
        return HexFormat.of().formatHex(
                runCipher(Cipher.ENCRYPT_MODE, plaintext.getBytes(StandardCharsets.UTF_8), key, iv));
    }

    public static String decrypt(String ciphertextHex, String key, String iv) {
        if (ciphertextHex == null || ciphertextHex.isBlank()) {
            throw new IllegalArgumentException("Classroom ciphertext is required");
        }
        byte[] ciphertext;
        try {
            ciphertext = HexFormat.of().parseHex(ciphertextHex.trim().toLowerCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Classroom ciphertext is not lowercase hex", e);
        }
        return new String(runCipher(Cipher.DECRYPT_MODE, ciphertext, key, iv), StandardCharsets.UTF_8);
    }

    private static byte[] runCipher(int mode, byte[] input, String key, String iv) {
        byte[] keyBytes = material(key, "key");
        byte[] ivBytes = material(iv, "iv");
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(mode, new SecretKeySpec(keyBytes, "AES"), new IvParameterSpec(ivBytes));
            return cipher.doFinal(input);
        } catch (Exception e) {
            throw new IllegalArgumentException("Classroom AES processing failed", e);
        }
    }

    private static byte[] material(String value, String name) {
        if (value == null) {
            throw new IllegalArgumentException("Classroom AES " + name + " is required");
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length != AES_128_BYTES) {
            throw new IllegalArgumentException(
                    "Classroom AES " + name + " must contain exactly " + AES_128_BYTES + " bytes");
        }
        return bytes;
    }

    private static void requireDerivable(String token) {
        if (!canDeriveKey(token)) {
            throw new IllegalArgumentException("Classroom token cannot derive an AES key");
        }
    }
}
