package com.han.common.web.sensitive;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import tools.jackson.databind.annotation.JsonSerialize;

import java.lang.annotation.*;

/**
 * 数据脱敏注解 — 标注在 DTO/VO 的 String 字段上，序列化时自动脱敏
 *
 * <p>使用示例：
 * <pre>
 * &#64;Sensitive(SensitiveType.PHONE)
 * private String phone;
 *
 * &#64;Sensitive(value = SensitiveType.CUSTOM, prefixKeep = 3, suffixKeep = 4)
 * private String custom;
 * </pre>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@JacksonAnnotationsInside
@JsonSerialize(using = SensitiveSerializer.class)
public @interface Sensitive {

    /** 脱敏类型 */
    SensitiveType value();

    /** CUSTOM 类型：保留前N位明文 */
    int prefixKeep() default 0;

    /** CUSTOM 类型：保留后N位明文 */
    int suffixKeep() default 0;
}
