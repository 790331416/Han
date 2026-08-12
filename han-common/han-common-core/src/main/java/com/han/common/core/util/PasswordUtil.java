package com.han.common.core.util;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.han.common.core.exception.BusinessException;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 密码工具类
 */
public final class PasswordUtil {

    /**
     * BCrypt加密密码
     */
    public static String encode(String rawPassword) {
        if (rawPassword == null) {
            throw new IllegalArgumentException("待加密的密码不能为 null");
        }
        return BCrypt.withDefaults().hashToString(10, rawPassword.toCharArray());
    }

    /**
     * BCrypt校验密码
     */
    public static boolean matches(String rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null) {
            return false;
        }
        return BCrypt.verifyer().verify(rawPassword.toCharArray(), encodedPassword).verified;
    }

    /**
     * 加密密码（encode别名）
     */
    public static String encrypt(String rawPassword) {
        return encode(rawPassword);
    }

    /** 密码最小长度 */
    private static final int MIN_LENGTH = 8;
    /** 密码最大长度 */
    private static final int MAX_LENGTH = 20;

    /**
     * 校验密码复杂度（不满足则抛异常）
     * <p>规则：8~20位，必须包含大写字母、小写字母、数字、特殊字符中的至少3种
     */
    public static void validate(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw new BusinessException("密码长度不能少于" + MIN_LENGTH + "位");
        }
        if (password.length() > MAX_LENGTH) {
            throw new BusinessException("密码长度不能超过" + MAX_LENGTH + "位");
        }
        int categories = 0;
        boolean hasLower = false, hasUpper = false, hasDigit = false, hasSpecial = false;
        for (char c : password.toCharArray()) {
            if (Character.isLowerCase(c)) hasLower = true;
            else if (Character.isUpperCase(c)) hasUpper = true;
            else if (Character.isDigit(c)) hasDigit = true;
            else hasSpecial = true;
        }
        if (hasLower) categories++;
        if (hasUpper) categories++;
        if (hasDigit) categories++;
        if (hasSpecial) categories++;
        if (categories < 3) {
            throw new BusinessException("密码必须包含大写字母、小写字母、数字、特殊字符中的至少3种");
        }
    }

    private static final String LOWER_CASE = "abcdefghijklmnopqrstuvwxyz";
    private static final String UPPER_CASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String DIGITS = "0123456789";
    private static final String SPECIAL_CHARS = "!@#$%^&*()_+-=";

    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordUtil() {}

    public static String generatePassword(int length) {
        if (length < 8) {
            length = 8;
        }
        if (length > 20) {
            length = 20;
        }

        StringBuilder password = new StringBuilder(length);
        String allChars = LOWER_CASE + UPPER_CASE + DIGITS + SPECIAL_CHARS;

        password.append(getRandomChar(LOWER_CASE));
        password.append(getRandomChar(UPPER_CASE));
        password.append(getRandomChar(DIGITS));
        password.append(getRandomChar(SPECIAL_CHARS));

        for (int i = 4; i < length; i++) {
            password.append(getRandomChar(allChars));
        }

        return shuffleString(password.toString());
    }

    private static char getRandomChar(String chars) {
        int index = RANDOM.nextInt(chars.length());
        return chars.charAt(index);
    }

    private static String shuffleString(String input) {
        char[] characters = input.toCharArray();
        for (int i = characters.length - 1; i > 0; i--) {
            int j = RANDOM.nextInt(i + 1);
            char temp = characters[i];
            characters[i] = characters[j];
            characters[j] = temp;
        }
        return new String(characters);
    }

    public static int checkPasswordStrength(String password) {
        if (password == null || password.isEmpty()) {
            return 0;
        }

        int score = 0;

        if (password.length() >= 8) {
            score += 10;
        } else if (password.length() >= 6) {
            score += 5;
        }

        boolean hasLower = false;
        boolean hasUpper = false;
        boolean hasDigit = false;
        boolean hasSpecial = false;

        for (char c : password.toCharArray()) {
            if (Character.isLowerCase(c)) hasLower = true;
            else if (Character.isUpperCase(c)) hasUpper = true;
            else if (Character.isDigit(c)) hasDigit = true;
            else hasSpecial = true;
        }

        if (hasLower) score += 10;
        if (hasUpper) score += 10;
        if (hasDigit) score += 10;
        if (hasSpecial) score += 15;

        int uniqueChars = password.chars().distinct().toArray().length;
        if (uniqueChars >= password.length() * 0.7) {
            score += 10;
        }

        return Math.min(score, 100);
    }

    public static boolean isStrongPassword(String password) {
        return checkPasswordStrength(password) >= 50;
    }

    /**
     * Base64 编码（<b>不是加密</b>，完全可逆）
     *
     * @deprecated 名字与相邻的 {@link #encode(String)}（真正的 BCrypt 哈希）高度相似，
     * 误用会把明文口令写进数据库。密码入库一律用 {@link #encode(String)}；
     * 确需可逆编码请用 {@link HanSecureUtil#base64Encode(String)}。本方法零调用点，仅为兼容保留。
     */
    @Deprecated(since = "1.0.0")
    public static String encodePassword(String password) {
        if (password == null) {
            return null;
        }
        return Base64.getEncoder().encodeToString(password.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Base64 解码
     *
     * @deprecated 见 {@link #encodePassword(String)}。
     */
    @Deprecated(since = "1.0.0")
    public static String decodePassword(String encodedPassword) {
        if (encodedPassword == null) {
            return null;
        }
        byte[] decodedBytes = Base64.getDecoder().decode(encodedPassword);
        return new String(decodedBytes, StandardCharsets.UTF_8);
    }
}
