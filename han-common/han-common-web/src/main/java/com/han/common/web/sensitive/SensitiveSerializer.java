package com.han.common.web.sensitive;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * 基于 Jackson 3 的字段脱敏序列化器。
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
            Sensitive annotation = property.getAnnotation(Sensitive.class);
            if (annotation != null) {
                return new SensitiveSerializer(annotation.value(), annotation.prefixKeep(), annotation.suffixKeep());
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
        if (value.isEmpty()) {
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

    private static String maskPhone(String phone) {
        if (phone.length() < 7) {
            return maskCustom(phone, 1, 1);
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private static String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 0) {
            return maskCustom(email, 1, 0);
        }
        return email.substring(0, 1) + "***" + email.substring(atIndex);
    }

    private static String maskIdCard(String idCard) {
        if (idCard.length() < 7) {
            return maskCustom(idCard, 1, 1);
        }
        return idCard.substring(0, 3) + "*".repeat(idCard.length() - 7) + idCard.substring(idCard.length() - 4);
    }

    private static String maskBankCard(String card) {
        if (card.length() < 8) {
            return maskCustom(card, 2, 2);
        }
        return card.substring(0, 4) + " **** **** " + card.substring(card.length() - 4);
    }

    private static String maskName(String name) {
        if (name.length() <= 1) {
            return "*";
        }
        return "*".repeat(name.length() - 1) + name.charAt(name.length() - 1);
    }

    private static String maskAddress(String address) {
        if (address.length() <= 6) {
            return maskCustom(address, 3, 0);
        }
        return address.substring(0, 6) + "****";
    }

    private static String maskCustom(String value, int prefix, int suffix) {
        int length = value.length();
        if (prefix + suffix >= length) {
            return "*".repeat(length);
        }
        return value.substring(0, prefix)
                + "*".repeat(length - prefix - suffix)
                + (suffix > 0 ? value.substring(length - suffix) : "");
    }
}
