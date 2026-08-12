package com.han.system.sdfz.compat;

import java.util.Map;

/**
 * 一次兼容调用解开信封后的请求上下文。
 *
 * @param consumer 由请求特征判定的消费者，决定响应形态
 * @param token    请求头里的兼容凭证，同时也是本次响应的 AES 密钥来源
 * @param path     被调用的旧路径常量
 * @param params   由 {@code param} 密文解出的业务参数
 */
public record LegacyRequest(LegacyProtocol.Consumer consumer, String token, String path,
                            Map<String, String> params) {

    public LegacyRequest {
        params = params != null ? Map.copyOf(params) : Map.of();
    }

    public boolean fromLegacyApi() {
        return consumer == LegacyProtocol.Consumer.LEGACY_API;
    }

    /** 取字符串参数，空串按缺失处理。 */
    public String text(String name) {
        String value = params.get(name);
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** 取数字参数，非法数字按缺失处理，避免旧前端传 {@code undefined} 字面量时抛异常。 */
    public Long number(String name) {
        String value = text(name);
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public int pageNo() {
        Long value = number("pageNo");
        return value == null || value < 1 ? 1 : value.intValue();
    }

    public int pageSize() {
        Long value = number("pageSize");
        if (value == null || value < 1) {
            return 20;
        }
        return (int) Math.min(value, 500L);
    }

    /** 返回第一个有值的参数，用于旧侧同义不同名的入参。 */
    public String firstText(String... names) {
        for (String name : names) {
            String value = text(name);
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
