package com.han.common.web.sensitive;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * 数据脱敏 Jackson 3 序列化器
 *
 * <p>通过 {@link Sensitive} 注解上的 {@code @JsonSerialize(using = SensitiveSerializer.class)}
 * 自动绑定到被标注的字段。序列化时根据 {@link SensitiveType} 执行对应脱敏策略。
 */
public class SensitiveSerializer extends StdSerializer<String> {

    private final SensitiveType type;
    private final int prefixKeep;
    private final int suffixKeep;

    public SensitiveSerializer() {
        super(String.class);
        this.type = SensitiveType.CUSTOM;
        this.prefixKeep = 0;
        this.suffixKeep = 0;
    }

    private SensitiveSerializer(SensitiveType type, int prefixKeep, int suffixKeep) {
        super(String.class);
        this.type = type;
        this.prefixKeep = prefixKeep;
        this.suffixKeep = suffixKeep;
    }

    @Override
    public ValueSerializer<?> createContextual(SerializationContext ctxt, BeanProperty property) {
        if (property != null) {
            Sensitive ann = property.getAnnotation(Sensitive.class);
            if (ann != null) {
                return new SensitiveSerializer(ann.value(), ann.prefixKeep(), ann.suffixKeep());
            }
        }
        return this;
    }

    @Override
    public void serialize(String value, JsonGenerator gen, SerializationContext ctxt) {
        if (value == null) {
            gen.writeNull();
            return;
        }
        gen.writeString(mask(value));
    }

    private String mask(String value) {
        if (value.isEmpty()) return value;
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

    /** 手机号：138****1234 */
    private static String maskPhone(String phone) {
        if (phone.length() < 7) return maskCustom(phone, 1, 1);
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /** 邮箱：t***@example.com */
    private static String maskEmail(String email) {
        int atIdx = email.indexOf('@');
        if (atIdx <= 0) return maskCustom(email, 1, 0);
        String prefix = email.substring(0, 1);
        return prefix + "***" + email.substring(atIdx);
    }

    /** 身份证：110***********1234 */
    private static String maskIdCard(String idCard) {
        if (idCard.length() < 7) return maskCustom(idCard, 1, 1);
        return idCard.substring(0, 3) + "*".repeat(idCard.length() - 7) + idCard.substring(idCard.length() - 4);
    }

    /** 银行卡：6222 **** **** 1234 */
    private static String maskBankCard(String card) {
        if (card.length() < 8) return maskCustom(card, 2, 2);
        return card.substring(0, 4) + " **** **** " + card.substring(card.length() - 4);
    }

    /** 姓名：*三 / **三 */
    private static String maskName(String name) {
        if (name.length() <= 1) return "*";
        return "*".repeat(name.length() - 1) + name.charAt(name.length() - 1);
    }

    /** 地址：保留前6个字符 */
    private static String maskAddress(String address) {
        if (address.length() <= 6) return maskCustom(address, 3, 0);
        return address.substring(0, 6) + "****";
    }

    /** 自定义：保留前N + 后N，中间用 * 填充 */
    private static String maskCustom(String value, int prefix, int suffix) {
        int len = value.length();
        if (prefix + suffix >= len) return "*".repeat(len);
        return value.substring(0, prefix)
                + "*".repeat(len - prefix - suffix)
                + (suffix > 0 ? value.substring(len - suffix) : "");
    }
}
