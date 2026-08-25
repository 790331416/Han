package com.han.common.core.util;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-GCM 密文工具。
 *
 * <p>调用方只保存 {@code v1:Base64(iv + ciphertext + tag)}，主密钥必须来自受控环境变量或 Secret，
 * 不得写入数据库、Nacos、日志或接口响应。</p>
 */
public final class AesGcmCipher {

    private static final String VERSION = "v1";
    private static final int KEY_LENGTH_BYTES = 32;
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private AesGcmCipher() {
    }

    public static String encrypt(String base64Key, String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(base64Key), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return VERSION + ":" + Base64.getEncoder().encodeToString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("存储密钥加密失败", ex);
        }
    }

    public static String decrypt(String base64Key, String ciphertext) {
        if (ciphertext == null || ciphertext.isBlank()) {
            return null;
        }
        if (!ciphertext.startsWith(VERSION + ":")) {
            throw new IllegalArgumentException("不支持的存储密钥密文版本");
        }
        try {
            byte[] payload = Base64.getDecoder().decode(ciphertext.substring((VERSION + ":").length()));
            if (payload.length <= IV_LENGTH_BYTES) {
                throw new IllegalArgumentException("存储密钥密文无效");
            }
            byte[] iv = new byte[IV_LENGTH_BYTES];
            byte[] encrypted = new byte[payload.length - IV_LENGTH_BYTES];
            System.arraycopy(payload, 0, iv, 0, iv.length);
            System.arraycopy(payload, iv.length, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(base64Key), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("存储密钥解密失败", ex);
        }
    }

    private static SecretKeySpec key(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException("未配置存储主密钥");
        }
        byte[] value;
        try {
            value = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("存储主密钥必须为 Base64 编码", ex);
        }
        if (value.length != KEY_LENGTH_BYTES) {
            throw new IllegalArgumentException("存储主密钥必须解码为 32 字节");
        }
        return new SecretKeySpec(value, "AES");
    }
}
