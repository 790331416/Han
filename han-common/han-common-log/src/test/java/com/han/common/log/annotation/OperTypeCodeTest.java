package com.han.common.log.annotation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * operType 原先落库用 {@code ordinal()}，往枚举中间插一个值就会让所有历史数据含义漂移。
 * 本测试锁死 code 与历史 ordinal 一致，确保这次改造对存量数据零影响。
 */
class OperTypeCodeTest {

    @Test
    @DisplayName("code 与历史 ordinal 逐个一致，存量数据含义不变")
    void codeMatchesLegacyOrdinal() {
        for (OperLog.OperType type : OperLog.OperType.values()) {
            assertEquals(type.ordinal(), type.getCode(), "枚举 " + type.name() + " 的落库值发生了漂移");
        }
    }

    @Test
    @DisplayName("落库值可反查枚举，未知值归为 OTHER")
    void fromCodeResolvesOrFallsBack() {
        assertEquals(OperLog.OperType.GRANT, OperLog.OperType.fromCode(8));
        assertEquals(OperLog.OperType.CLEAN, OperLog.OperType.fromCode(10));
        assertEquals(OperLog.OperType.OTHER, OperLog.OperType.fromCode(99));
        assertEquals(OperLog.OperType.OTHER, OperLog.OperType.fromCode(null));
    }
}
