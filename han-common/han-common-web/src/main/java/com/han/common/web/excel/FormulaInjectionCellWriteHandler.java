package com.han.common.web.excel;

import com.alibaba.excel.write.handler.CellWriteHandler;
import com.alibaba.excel.write.handler.context.CellWriteHandlerContext;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;

/**
 * 导出单元格的公式注入防护。
 * <p>
 * 导出内容里只要有一个字段来自用户输入（用户名、备注、租户名等），
 * 就可以构造以 {@code = + - @} 或制表符 / 回车开头的值；管理员用 Excel 打开导出文件时
 * 这些内容会被当作公式求值，可构造 DDE 载荷。攻击面是「下载并打开导出文件的管理员」。
 * <p>
 * 处理方式是给危险起始字符加单引号前缀，Excel 会按纯文本显示。
 */
public class FormulaInjectionCellWriteHandler implements CellWriteHandler {

    private static final String DANGEROUS_PREFIXES = "=+-@\t\r";

    @Override
    public void afterCellDispose(CellWriteHandlerContext context) {
        Cell cell = context == null ? null : context.getCell();
        if (cell == null || cell.getCellType() != CellType.STRING) {
            return;
        }
        String value = cell.getStringCellValue();
        String safe = escape(value);
        if (!safe.equals(value)) {
            cell.setCellValue(safe);
        }
    }

    /**
     * 命中危险起始字符时加单引号前缀。
     */
    public static String escape(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return DANGEROUS_PREFIXES.indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
    }
}
