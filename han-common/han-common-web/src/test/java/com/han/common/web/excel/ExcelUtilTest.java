package com.han.common.web.excel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExcelUtilTest {

    @Test
    @DisplayName("sheet 名做非法字符替换与 31 字符截断")
    void sanitizesSheetName() {
        assertEquals("角色_数据", ExcelUtil.sanitizeSheetName("角色/数据"));
        assertEquals("a_b_c_d_e_f", ExcelUtil.sanitizeSheetName("a:b\\c?d*e[f"));
        assertEquals(31, ExcelUtil.sanitizeSheetName("x".repeat(50)).length());
        assertEquals("Sheet1", ExcelUtil.sanitizeSheetName("  "));
    }

    @Test
    @DisplayName("以危险字符开头的单元格值加单引号前缀，避免被 Excel 当公式求值")
    void escapesFormulaInjection() {
        assertEquals("'=cmd|'/c calc'!A0", FormulaInjectionCellWriteHandler.escape("=cmd|'/c calc'!A0"));
        assertEquals("'+1+1", FormulaInjectionCellWriteHandler.escape("+1+1"));
        assertEquals("'-1", FormulaInjectionCellWriteHandler.escape("-1"));
        assertEquals("'@SUM(A1)", FormulaInjectionCellWriteHandler.escape("@SUM(A1)"));
        assertEquals("正常内容", FormulaInjectionCellWriteHandler.escape("正常内容"));
        assertEquals("", FormulaInjectionCellWriteHandler.escape(""));
    }
}
