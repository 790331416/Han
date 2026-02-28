package com.han.common.core.util;

import at.favre.lib.crypto.bcrypt.BCrypt;

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

    /**
     * 校验密码强度（不满足则抛异常）
     */
    public static void validate(String password) {
        if (password == null || password.length() < 6) {
            throw new IllegalArgumentException("密码长度不能少于6位");
        }
        if (password.length() > 20) {
            throw new IllegalArgumentException("密码长度不能超过20位");
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

    public static String encodePassword(String password) {
        if (password == null) {
            return null;
        }
        return Base64.getEncoder().encodeToString(password.getBytes());
    }

    public static String decodePassword(String encodedPassword) {
        if (encodedPassword == null) {
            return null;
        }
        byte[] decodedBytes = Base64.getDecoder().decode(encodedPassword);
        return new String(decodedBytes);
    }
}
