package com.han.common.core.util;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.security.spec.MGF1ParameterSpec;
import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;

/**
 * 安全工具类
 */
public final class HanSecureUtil {

    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    private HanSecureUtil() {}

    /**
     * MD5加密
     */
    public static String md5(String str) {
        if (str == null) {
            return null;
        }
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(str.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5加密失败", e);
        }
    }

    /**
     * SHA256加密
     */
    public static String sha256(String str) {
        if (str == null) {
            return null;
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(str.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA256加密失败", e);
        }
    }

    /**
     * Base64编码
     */
    public static String base64Encode(String str) {
        if (str == null) {
            return null;
        }
        return Base64.getEncoder().encodeToString(str.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Base64解码
     */
    public static String base64Decode(String str) {
        if (str == null) {
            return null;
        }
        byte[] bytes = Base64.getDecoder().decode(str);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * 字节数组转十六进制字符串
     */
    private static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            hexChars[i * 2] = HEX_CHARS[v >>> 4];
            hexChars[i * 2 + 1] = HEX_CHARS[v & 0x0F];
        }
        return new String(hexChars);
    }

    /** 密码字符取样必须用密码学安全随机源 */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * 生成随机密码
     *
     * @deprecated 请改用 {@link PasswordUtil#generatePassword(int)} —— 那一份保证四类字符齐全，
     * 本方法只保证随机性，可能生成不满足强度规则的口令。
     */
    @Deprecated(since = "1.0.0")
    public static String generatePassword(int length) {
        if (length < 8) {
            length = 8;
        }
        StringBuilder sb = new StringBuilder();
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*";
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(SECURE_RANDOM.nextInt(chars.length())));
        }
        return sb.toString();
    }

    /**
     * 验证密码强度（≥8 位且四类字符全部具备）
     *
     * @deprecated 平台密码策略的唯一入口是 {@link PasswordUtil#validate(String)}。
     * 本方法的规则与之不同（这里要求四类全有，那边要求四选三），并存会让同一个口令在不同入口得到不同结论。
     */
    @Deprecated(since = "1.0.0")
    public static boolean isStrongPassword(String password) {
        if (password == null || password.length() < 8) {
            return false;
        }
        boolean hasUpper = false;
        boolean hasLower = false;
        boolean hasDigit = false;
        boolean hasSpecial = false;
        for (char c : password.toCharArray()) {
            if (Character.isUpperCase(c)) {
                hasUpper = true;
            } else if (Character.isLowerCase(c)) {
                hasLower = true;
            } else if (Character.isDigit(c)) {
                hasDigit = true;
            } else {
                hasSpecial = true;
            }
        }
        return hasUpper && hasLower && hasDigit && hasSpecial;
    }

    // ==================== RSA 加解密 ====================

    private static final String RSA_ALGORITHM = "RSA";
    private static final String RSA_CIPHER = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
    private static final OAEPParameterSpec OAEP_PARAMS = new OAEPParameterSpec(
            "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);

    /**
     * 生成 RSA 密钥对（2048位）
     */
    public static KeyPair generateRsaKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(RSA_ALGORITHM);
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("生成RSA密钥对失败", e);
        }
    }

    /**
     * 获取 Base64 编码的公钥字符串
     */
    public static String getPublicKeyBase64(KeyPair keyPair) {
        return Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
    }

    /**
     * 获取 Base64 编码的私钥字符串
     */
    public static String getPrivateKeyBase64(KeyPair keyPair) {
        return Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
    }

    /**
     * RSA 私钥解密（解密前端加密的密码）
     */
    public static String rsaDecrypt(String encryptedBase64, String privateKeyBase64) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(privateKeyBase64);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
            PrivateKey privateKey = KeyFactory.getInstance(RSA_ALGORITHM).generatePrivate(keySpec);
            Cipher cipher = Cipher.getInstance(RSA_CIPHER);
            cipher.init(Cipher.DECRYPT_MODE, privateKey, OAEP_PARAMS);
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(encryptedBase64));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("RSA解密失败", e);
        }
    }

    /**
     * RSA 公钥加密（用于测试）
     */
    public static String rsaEncrypt(String plainText, String publicKeyBase64) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(publicKeyBase64);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
            PublicKey publicKey = KeyFactory.getInstance(RSA_ALGORITHM).generatePublic(keySpec);
            Cipher cipher = Cipher.getInstance(RSA_CIPHER);
            cipher.init(Cipher.ENCRYPT_MODE, publicKey, OAEP_PARAMS);
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("RSA加密失败", e);
        }
    }
}
