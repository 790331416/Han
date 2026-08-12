package com.han.common.core.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

/**
 * JSON工具类
 *
 * @deprecated 与 {@link HanJsonUtil} 完全重复，已全部委托给 {@code HanJsonUtil}，
 * 因此两者的日期格式与 {@code @Sensitive} 脱敏行为始终一致。
 * 新代码请直接使用 {@link HanJsonUtil}，存量调用点将在统一整改批次中迁移。
 */
@Deprecated(since = "1.0.0")
public final class XuJsonUtil {

    private XuJsonUtil() {}

    /**
     * 对象转JSON字符串
     */
    public static String toJsonString(Object obj) {
        return HanJsonUtil.toJsonString(obj);
    }

    /**
     * JSON字符串转对象
     */
    public static <T> T parseObject(String json, Class<T> clazz) {
        return HanJsonUtil.parseObject(json, clazz);
    }

    /**
     * JSON字符串转对象
     */
    public static <T> T parseObject(String json, TypeReference<T> typeReference) {
        return HanJsonUtil.parseObject(json, typeReference);
    }

    /**
     * JSON字符串转List
     */
    public static <T> List<T> parseList(String json, Class<T> clazz) {
        return HanJsonUtil.parseList(json, clazz);
    }

    /**
     * JSON字符串转Map
     */
    public static Map<String, Object> parseMap(String json) {
        return HanJsonUtil.parseMap(json);
    }

    /**
     * 获取ObjectMapper实例
     */
    public static ObjectMapper getObjectMapper() {
        return HanJsonUtil.getObjectMapper();
    }
}
