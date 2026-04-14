package com.han.common.web.sensitive;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import tools.jackson.databind.annotation.JsonSerialize;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 数据脱敏注解，标注在 DTO 或 VO 的字符串字段上，序列化时自动脱敏。
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@JacksonAnnotationsInside
@JsonSerialize(using = SensitiveSerializer.class)
public @interface Sensitive {

    /**
     * 脱敏类型。
     */
    SensitiveType value();

    /**
     * 自定义脱敏时保留的前缀长度。
     */
    int prefixKeep() default 0;

    /**
     * 自定义脱敏时保留的后缀长度。
     */
    int suffixKeep() default 0;
}
