package com.han.common.web.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.ToStringSerializer;
import tools.jackson.databind.ext.javatime.deser.LocalDateDeserializer;
import tools.jackson.databind.ext.javatime.deser.LocalDateTimeDeserializer;
import tools.jackson.databind.ext.javatime.deser.LocalTimeDeserializer;
import tools.jackson.databind.ext.javatime.ser.LocalDateSerializer;
import tools.jackson.databind.ext.javatime.ser.LocalDateTimeSerializer;
import tools.jackson.databind.ext.javatime.ser.LocalTimeSerializer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Jackson 3 全局配置（Spring Boot 4.0）
 * <p>
 * Long/long → String：防止雪花 ID 超过 JS Number.MAX_SAFE_INTEGER 导致前端精度丢失。
 * 通过 {@link JsonMapperBuilderCustomizer} 注册到 Jackson 3 的 {@code JsonMapper}。
 */
@Configuration
public class JacksonAutoConfiguration {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_TIME_DESERIALIZE_FORMATTER = new java.time.format.DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd")
            .appendLiteral(' ')
            .appendPattern("HH:mm:ss")
            .optionalStart().appendFraction(java.time.temporal.ChronoField.NANO_OF_SECOND, 0, 9, true).optionalEnd()
            .toFormatter();
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Bean
    public JsonMapperBuilderCustomizer longToStringCustomizer() {
        return builder -> {
            SimpleModule module = new SimpleModule("HanGlobalModule");
            // Long（包装类型，ID 字段）序列化为 String
            module.addSerializer(Long.class, ToStringSerializer.instance);
            // 统一日期时间格式: yyyy-MM-dd HH:mm:ss
            module.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(DATE_TIME_FORMATTER));
            module.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(DATE_TIME_DESERIALIZE_FORMATTER));
            module.addSerializer(LocalDate.class, new LocalDateSerializer(DATE_FORMATTER));
            module.addDeserializer(LocalDate.class, new LocalDateDeserializer(DATE_FORMATTER));
            module.addSerializer(LocalTime.class, new LocalTimeSerializer(TIME_FORMATTER));
            module.addDeserializer(LocalTime.class, new LocalTimeDeserializer(TIME_FORMATTER));
            builder.addModule(module);
        };
    }
}
