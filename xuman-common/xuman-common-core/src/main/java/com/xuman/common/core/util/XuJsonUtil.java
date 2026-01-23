package com.xuman.common.core.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * JSON工具类（封装Jackson）
 */
public final class XuJsonUtil {

    private static final ObjectMapper MAPPER;

    static {
        MAPPER = new ObjectMapper();
        // 注册Java8时间模块
        MAPPER.registerModule(new JavaTimeModule());
        // 禁用日期转时间戳
        MAPPER.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        // 空对象不报错
        MAPPER.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        // 未知属性不报错
        MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    private XuJsonUtil() {}

    /**
     * 获取ObjectMapper实例
     */
    public static ObjectMapper getMapper() {
        return MAPPER;
    }

    /**
     * 对象转JSON字符串
     */
    public static String toJsonStr(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof String) {
            return (String) obj;
        }
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON序列化失败", e);
        }
    }

    /**
     * 对象转JSON字符串（格式化）
     */
    public static String toJsonPrettyStr(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON序列化失败", e);
        }
    }

    /**
     * JSON字符串转对象
     */
    public static <T> T parseObj(String json, Class<T> clazz) {
        if (XuStrUtil.isBlank(json)) {
            return null;
        }
        try {
            return MAPPER.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON反序列化失败", e);
        }
    }

    /**
     * JSON字符串转对象（泛型）
     */
    public static <T> T parseObj(String json, TypeReference<T> typeReference) {
        if (XuStrUtil.isBlank(json)) {
            return null;
        }
        try {
            return MAPPER.readValue(json, typeReference);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON反序列化失败", e);
        }
    }

    /**
     * JSON字符串转List
     */
    public static <T> List<T> parseArray(String json, Class<T> clazz) {
        if (XuStrUtil.isBlank(json)) {
            return new ArrayList<>();
        }
        try {
            return MAPPER.readValue(json, 
                MAPPER.getTypeFactory().constructCollectionType(List.class, clazz));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON反序列化失败", e);
        }
    }

    /**
     * JSON字符串转Map
     */
    public static Map<String, Object> parseMap(String json) {
        if (XuStrUtil.isBlank(json)) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON反序列化失败", e);
        }
    }

    /**
     * 对象深拷贝
     */
    public static <T> T copy(Object source, Class<T> clazz) {
        return parseObj(toJsonStr(source), clazz);
    }

    /**
     * 对象转Map
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> toMap(Object obj) {
        if (obj == null) {
            return Map.of();
        }
        return MAPPER.convertValue(obj, Map.class);
    }

    /**
     * Map转对象
     */
    public static <T> T mapToObj(Map<String, Object> map, Class<T> clazz) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        return MAPPER.convertValue(map, clazz);
    }
}
