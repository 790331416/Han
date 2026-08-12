package com.han.common.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class HanCollUtilTest {

    @Test
    @DisplayName("toList 无论入参是否为空都返回可增删的列表")
    void toListIsAlwaysMutable() {
        List<String> fromEmpty = HanCollUtil.toList();
        List<String> fromValues = HanCollUtil.toList("a", "b");

        assertDoesNotThrow(() -> fromEmpty.add("x"));
        // 原实现非空分支返回 Arrays.asList 定长视图，调用方 add 会抛 UnsupportedOperationException
        assertDoesNotThrow(() -> fromValues.add("c"));
        assertEquals(3, fromValues.size());
    }

    @Test
    @DisplayName("join 遇到 null 元素不再抛 NPE")
    void joinHandlesNullElements() {
        assertEquals("a,null,b", HanCollUtil.join(Arrays.asList("a", null, "b"), ","));
        assertEquals("", HanCollUtil.join(List.of(), ","));
    }

    @Test
    @DisplayName("XuCollUtil 委托后与 HanCollUtil 行为一致")
    void xuDelegatesToHan() {
        List<String> list = XuCollUtil.toList("a", "b");
        assertDoesNotThrow(() -> list.add("c"));
        assertEquals("a,null", XuCollUtil.join(Arrays.asList("a", null), ","));
    }
}
