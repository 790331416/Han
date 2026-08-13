package com.han.common.web.sensitive;

/**
 * 脱敏算法唯一实现。
 * <p>
 * Web 层的 Jackson 3 {@link SensitiveSerializer} 与工具类侧的 Jackson 2
 * {@link SensitiveJackson2Module} 都调用这里，保证「接口出参」和「写缓存 / 打日志」
 * 两条路径的脱敏结果完全一致。
 */
public final class SensitiveMasker {

    private SensitiveMasker() {}

    /**
     * 按脱敏类型处理字符串。
     *
     * @param type       脱敏类型，为 {@code null} 时原样返回
     * @param value      原始值，为 {@code null} 或空串时原样返回
     * @param prefixKeep {@link SensitiveType#CUSTOM} 下保留的前缀长度
     * @param suffixKeep {@link SensitiveType#CUSTOM} 下保留的后缀长度
     */
    public static String mask(SensitiveType type, String value, int prefixKeep, int suffixKeep) {
        if (value == null || value.isEmpty() || type == null) {
            return value;
        }
        return switch (type) {
            case PHONE -> maskPhone(value);
            case EMAIL -> maskEmail(value);
            case ID_CARD -> maskIdCard(value);
            case BANK_CARD -> maskBankCard(value);
            case NAME -> maskName(value);
            case ADDRESS -> maskAddress(value);
            case CUSTOM -> maskCustom(value, prefixKeep, suffixKeep);
        };
    }

    public static String maskPhone(String phone) {
        if (phone.length() < 7) {
            return maskCustom(phone, 1, 1);
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    public static String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 0) {
            return maskCustom(email, 1, 0);
        }
        return email.substring(0, 1) + "***" + email.substring(atIndex);
    }

    public static String maskIdCard(String idCard) {
        if (idCard.length() < 7) {
            return maskCustom(idCard, 1, 1);
        }
        return idCard.substring(0, 3) + "*".repeat(idCard.length() - 7) + idCard.substring(idCard.length() - 4);
    }

    public static String maskBankCard(String card) {
        if (card.length() < 8) {
            return maskCustom(card, 2, 2);
        }
        return card.substring(0, 4) + " **** **** " + card.substring(card.length() - 4);
    }

    /**
     * 姓名脱敏：保留姓氏，隐藏名字（"张三丰" → "张**"），与国内通行惯例一致。
     */
    public static String maskName(String name) {
        if (name.length() <= 1) {
            return "*";
        }
        return name.charAt(0) + "*".repeat(name.length() - 1);
    }

    public static String maskAddress(String address) {
        if (address.length() <= 6) {
            return maskCustom(address, 3, 0);
        }
        return address.substring(0, 6) + "****";
    }

    public static String maskCustom(String value, int prefix, int suffix) {
        int length = value.length();
        int safePrefix = Math.max(prefix, 0);
        int safeSuffix = Math.max(suffix, 0);
        if (safePrefix + safeSuffix >= length) {
            return "*".repeat(length);
        }
        return value.substring(0, safePrefix)
                + "*".repeat(length - safePrefix - safeSuffix)
                + (safeSuffix > 0 ? value.substring(length - safeSuffix) : "");
    }
}
