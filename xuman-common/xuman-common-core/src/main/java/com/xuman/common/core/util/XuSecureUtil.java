package com.xuman.common.core.util;

import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.symmetric.AES;
import at.favre.lib.crypto.bcrypt.BCrypt;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;

/**
 * 安全工具类（封装加密算法）
 */
public final class XuSecureUtil {

    private XuSecureUtil() {}

    /**
     * MD5加密
     */
    public static String md5(String data) {
        return SecureUtil.md5(data);
    }

    /**
     * SHA256加密
     */
    public static String sha256(String data) {
        return SecureUtil.sha256(data);
    }

    /**
     * BCrypt密码加密（推荐用于密码存储）
     */
    public static String bcryptHash(String password) {
        return BCrypt.withDefaults().hashToString(12, password.toCharArray());
    }

    /**
     * BCrypt密码验证
     */
    public static boolean bcryptCheck(String password, String hashed) {
        BCrypt.Result result = BCrypt.verifyer().verify(password.toCharArray(), hashed);
        return result.verified;
    }

    /**
     * AES加密
     */
    public static String aesEncrypt(String data, String key) {
        AES aes = SecureUtil.aes(padKey(key).getBytes(StandardCharsets.UTF_8));
        return aes.encryptBase64(data);
    }

    /**
     * AES解密
     */
    public static String aesDecrypt(String encryptedData, String key) {
        AES aes = SecureUtil.aes(padKey(key).getBytes(StandardCharsets.UTF_8));
        return aes.decryptStr(encryptedData);
    }

    /**
     * 补齐密钥到16位
     */
    private static String padKey(String key) {
        if (key.length() >= 16) {
            return key.substring(0, 16);
        }
        return String.format("%-16s", key).replace(' ', '0');
    }

    /**
     * RSA密钥对生成
     */
    public static KeyPair generateRsaKeyPair() {
        return SecureUtil.generateKeyPair("RSA", 2048);
    }

    /**
     * Base64编码
     */
    public static String base64Encode(String data) {
        return cn.hutool.core.codec.Base64.encode(data);
    }

    /**
     * Base64解码
     */
    public static String base64Decode(String data) {
        return cn.hutool.core.codec.Base64.decodeStr(data);
    }
}
