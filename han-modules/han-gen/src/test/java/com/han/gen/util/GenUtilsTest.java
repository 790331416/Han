package com.han.gen.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GenUtilsTest {

    @Test
    void mapsPostgresAndMysqlColumnTypes() {
        assertEquals("Long", GenUtils.dbTypeToJavaType("int8"));
        assertEquals("Integer", GenUtils.dbTypeToJavaType("tinyint(1)"));
        assertEquals("Integer", GenUtils.dbTypeToJavaType("int unsigned"));
        assertEquals("LocalDateTime", GenUtils.dbTypeToJavaType("datetime"));
        assertEquals("byte[]", GenUtils.dbTypeToJavaType("longblob"));
        assertEquals("String", GenUtils.dbTypeToJavaType("json"));
    }
}
