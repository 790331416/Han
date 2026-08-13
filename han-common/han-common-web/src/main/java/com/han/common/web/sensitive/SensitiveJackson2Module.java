package com.han.common.web.sensitive;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;

/**
 * 让 {@link Sensitive} 在 Jackson 2 侧同样生效的模块。
 * <p>
 * {@code @Sensitive} 绑定的是 Jackson 3 的 {@code @JsonSerialize}，Jackson 2 的
 * {@code ObjectMapper} 不认识它。而 {@code HanJsonUtil} / {@code XuJsonUtil} 用的正是 Jackson 2，
 * 典型用途又是写 Redis 缓存和打操作日志 —— 不补这一层，脱敏就存在一条完整绕过路径，
 * 手机号、身份证、API Key 会以明文落盘。
 * <p>
 * 本模块通过 {@code META-INF/services} 注册到 {@code HanJsonUtil}，无需调用方感知。
 */
public class SensitiveJackson2Module extends SimpleModule {

    private static final long serialVersionUID = 1L;

    public SensitiveJackson2Module() {
        super("HanSensitiveJackson2Module");
        setSerializerModifier(new SensitiveSerializerModifier());
    }

    private static final class SensitiveSerializerModifier extends BeanSerializerModifier {

        private static final long serialVersionUID = 1L;

        @Override
        public List<BeanPropertyWriter> changeProperties(SerializationConfig config,
                                                         BeanDescription beanDesc,
                                                         List<BeanPropertyWriter> beanProperties) {
            for (BeanPropertyWriter writer : beanProperties) {
                if (!String.class.equals(writer.getType().getRawClass())) {
                    continue;
                }
                Sensitive sensitive = findSensitive(beanDesc, writer);
                if (sensitive != null) {
                    writer.assignSerializer(new Jackson2SensitiveSerializer(
                            sensitive.value(), sensitive.prefixKeep(), sensitive.suffixKeep()));
                }
            }
            return beanProperties;
        }

        /**
         * 优先读属性上合并后的注解；取不到时回退到按属性名在类继承链上找字段，
         * 避免 getter/field 注解合并策略变化让脱敏静默失效。
         */
        private Sensitive findSensitive(BeanDescription beanDesc, BeanPropertyWriter writer) {
            Sensitive sensitive = writer.getAnnotation(Sensitive.class);
            if (sensitive != null) {
                return sensitive;
            }
            AnnotatedMember member = writer.getMember();
            if (member != null) {
                sensitive = member.getAnnotation(Sensitive.class);
                if (sensitive != null) {
                    return sensitive;
                }
            }
            for (Class<?> type = beanDesc.getBeanClass(); type != null && type != Object.class;
                 type = type.getSuperclass()) {
                try {
                    Field field = type.getDeclaredField(writer.getName());
                    sensitive = field.getAnnotation(Sensitive.class);
                    if (sensitive != null) {
                        return sensitive;
                    }
                } catch (NoSuchFieldException ignored) {
                    // 继续向父类找
                }
            }
            return null;
        }
    }

    private static final class Jackson2SensitiveSerializer extends JsonSerializer<Object> {

        private final SensitiveType type;
        private final int prefixKeep;
        private final int suffixKeep;

        private Jackson2SensitiveSerializer(SensitiveType type, int prefixKeep, int suffixKeep) {
            this.type = type;
            this.prefixKeep = prefixKeep;
            this.suffixKeep = suffixKeep;
        }

        @Override
        public void serialize(Object value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            if (value == null) {
                gen.writeNull();
                return;
            }
            gen.writeString(SensitiveMasker.mask(type, String.valueOf(value), prefixKeep, suffixKeep));
        }
    }
}
