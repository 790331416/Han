package com.han.common.web.sensitive;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * 基于 Jackson 3 的字段脱敏序列化器。
 * <p>脱敏算法统一放在 {@link SensitiveMasker}，与 Jackson 2 侧共用同一份实现。
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
        gen.writeString(SensitiveMasker.mask(type, value, prefixKeep, suffixKeep));
    }
}
