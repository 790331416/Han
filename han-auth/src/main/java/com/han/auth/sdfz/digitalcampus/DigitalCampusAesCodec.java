package com.han.auth.sdfz.digitalcampus;

import com.han.common.core.exception.BusinessException;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;

/**
 * 数字校园旧协议的 AES-CBC 编解码器。
 *
 * <p>密钥取 Token 倒数第 48 至 33 位，IV 取最后 16 位；该规则来自现有三课堂网关契约。
 */
final class DigitalCampusAesCodec {

    private static final int MIN_TOKEN_LENGTH = 48;

    private DigitalCampusAesCodec() {
    }

    static String encrypt(String value, String token) {
        return HexFormat.of().formatHex(runCipher(Cipher.ENCRYPT_MODE,
                value.getBytes(StandardCharsets.UTF_8), token));
    }

    static String decrypt(String value, String token) {
        try {
            return new String(runCipher(Cipher.DECRYPT_MODE, HexFormat.of().parseHex(value), token),
                    StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("数字校园响应解密失败");
        }
    }

    static String encryptedTimestamp(String token) {
        String nonce = UUID.randomUUID().toString().substring(0, 19) + System.currentTimeMillis();
        return encrypt(nonce, token);
    }

    private static byte[] runCipher(int mode, byte[] input, String token) {
        validateToken(token);
        int length = token.length();
        byte[] key = token.substring(length - 48, length - 32).getBytes(StandardCharsets.UTF_8);
        byte[] iv = token.substring(length - 16).getBytes(StandardCharsets.UTF_8);
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(mode, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
            return cipher.doFinal(input);
        } catch (Exception e) {
            throw new BusinessException("数字校园协议处理失败");
        }
    }

    private static void validateToken(String token) {
        if (token == null || token.isBlank() || token.length() < MIN_TOKEN_LENGTH) {
            throw new BusinessException("数字校园 Token 格式无效");
        }
    }
}
