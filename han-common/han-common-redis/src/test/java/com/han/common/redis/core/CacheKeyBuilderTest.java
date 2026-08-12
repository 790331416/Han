package com.han.common.redis.core;

import com.han.common.core.constant.CacheConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CacheKeyBuilderTest {

    @Test
    @DisplayName("统一补齐 han: 前缀与分隔冒号")
    void normalizesPrefix() {
        assertEquals("han:dict:sys_yes_no", CacheKeyBuilder.build("dict", "sys_yes_no"));
        assertEquals("han:dict:sys_yes_no", CacheKeyBuilder.build("dict:", "sys_yes_no"));
        assertEquals("han:dict:sys_yes_no", CacheKeyBuilder.build(CacheConstants.DICT_KEY, "sys_yes_no"));
    }

    @Test
    @DisplayName("带租户维度时插在业务前缀与标识之间")
    void insertsTenantSegment() {
        assertEquals("han:dict:100:sys_yes_no", CacheKeyBuilder.build(CacheConstants.DICT_KEY, 100L, "sys_yes_no"));
        assertEquals("han:dict:sys_yes_no", CacheKeyBuilder.build(CacheConstants.DICT_KEY, null, "sys_yes_no"));
        assertEquals("han:dict:sys_yes_no", CacheKeyBuilder.build(CacheConstants.DICT_KEY, "  ", "sys_yes_no"));
    }

    @Test
    @DisplayName("SCAN 模式按租户收敛")
    void buildsScanPattern() {
        assertEquals("han:dict:100:*", CacheKeyBuilder.pattern(CacheConstants.DICT_KEY, 100L));
        assertEquals("han:dict:*", CacheKeyBuilder.pattern(CacheConstants.DICT_KEY, null));
    }

    @Test
    @DisplayName("拒绝空前缀")
    void rejectsBlankPrefix() {
        assertThrows(IllegalArgumentException.class, () -> CacheKeyBuilder.build("  ", "x"));
    }
}
