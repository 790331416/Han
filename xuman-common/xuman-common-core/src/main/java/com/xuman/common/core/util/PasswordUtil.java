package com.xuman.common.core.util;

/**
 * 密码工具类
 */
public final class PasswordUtil {

    private PasswordUtil() {}

    /** 密码最小长度 */
    private static final int MIN_LENGTH = 8;
    /** 密码最大长度 */
    private static final int MAX_LENGTH = 32;

    /** 常见弱密码 */
    private static final String[] WEAK_PASSWORDS = {
        "password", "123456", "12345678", "qwerty", "abc123",
        "password123", "admin123", "root123", "111111", "000000",
        "123123", "admin", "root", "test", "guest", "letmein",
        "welcome", "monkey", "dragon", "master", "qwerty123"
    };

    /**
     * 密码强度校验
     */
    public static PasswordStrength checkStrength(String password) {
        if (XuStrUtil.isBlank(password)) {
            return PasswordStrength.WEAK;
        }

        int score = 0;

        // 长度检查
        if (password.length() >= MIN_LENGTH) score++;
        if (password.length() >= 12) score++;
        if (password.length() >= 16) score++;

        // 包含小写字母
        if (password.matches(".*[a-z].*")) score++;
        // 包含大写字母
        if (password.matches(".*[A-Z].*")) score++;
        // 包含数字
        if (password.matches(".*\\d.*")) score++;
        // 包含特殊字符
        if (password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*")) score++;

        if (score <= 2) return PasswordStrength.WEAK;
        if (score <= 4) return PasswordStrength.MEDIUM;
        if (score <= 6) return PasswordStrength.STRONG;
        return PasswordStrength.VERY_STRONG;
    }

    /**
     * 密码复杂度校验
     */
    public static void validate(String password) {
        if (XuStrUtil.isBlank(password)) {
            throw new IllegalArgumentException("密码不能为空");
        }
        if (password.length() < MIN_LENGTH) {
            throw new IllegalArgumentException("密码长度不能少于" + MIN_LENGTH + "位");
        }
        if (password.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("密码长度不能超过" + MAX_LENGTH + "位");
        }
        if (!password.matches(".*[a-z].*")) {
            throw new IllegalArgumentException("密码必须包含小写字母");
        }
        if (!password.matches(".*[A-Z].*")) {
            throw new IllegalArgumentException("密码必须包含大写字母");
        }
        if (!password.matches(".*\\d.*")) {
            throw new IllegalArgumentException("密码必须包含数字");
        }
        if (!password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*")) {
            throw new IllegalArgumentException("密码必须包含特殊字符");
        }
        if (isWeakPassword(password)) {
            throw new IllegalArgumentException("密码过于简单，请使用更复杂的密码");
        }
    }

    /**
     * 弱密码检测
     */
    public static boolean isWeakPassword(String password) {
        String lower = password.toLowerCase();
        for (String weak : WEAK_PASSWORDS) {
            if (lower.contains(weak)) {
                return true;
            }
        }
        // 检查连续字符
        if (hasSequentialChars(password, 4)) {
            return true;
        }
        // 检查重复字符
        return hasRepeatedChars(password, 4);
    }

    private static boolean hasSequentialChars(String str, int count) {
        for (int i = 0; i <= str.length() - count; i++) {
            boolean sequential = true;
            for (int j = 1; j < count; j++) {
                if (str.charAt(i + j) - str.charAt(i + j - 1) != 1) {
                    sequential = false;
                    break;
                }
            }
            if (sequential) return true;
        }
        return false;
    }

    private static boolean hasRepeatedChars(String str, int count) {
        for (int i = 0; i <= str.length() - count; i++) {
            boolean repeated = true;
            char c = str.charAt(i);
            for (int j = 1; j < count; j++) {
                if (str.charAt(i + j) != c) {
                    repeated = false;
                    break;
                }
            }
            if (repeated) return true;
        }
        return false;
    }

    /**
     * 密码加密（BCrypt）
     */
    public static String encrypt(String password) {
        return XuSecureUtil.bcryptHash(password);
    }

    /**
     * 密码验证
     */
    public static boolean matches(String rawPassword, String encodedPassword) {
        return XuSecureUtil.bcryptCheck(rawPassword, encodedPassword);
    }

    /**
     * 密码强度枚举
     */
    public enum PasswordStrength {
        WEAK, MEDIUM, STRONG, VERY_STRONG
    }
}
