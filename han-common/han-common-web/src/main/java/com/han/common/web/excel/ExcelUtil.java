package com.han.common.web.excel;

import com.alibaba.excel.EasyExcel;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 通用 Excel 导出工具类
 * <p>
 * 封装 EasyExcel 常见导出/导入模板逻辑，Controller 只需一行调用。
 *
 * <pre>
 * // 导出示例
 * ExcelUtil.exportExcel(response, "角色数据", RoleExportVo.class, dataList);
 *
 * // 导出空模板
 * ExcelUtil.exportTemplate(response, "角色导入模板", RoleImportVo.class);
 *
 * // 导入读取
 * List&lt;RoleImportVo&gt; list = ExcelUtil.importExcel(file.getInputStream(), RoleImportVo.class);
 * </pre>
 */
public final class ExcelUtil {

    private static final String CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private ExcelUtil() {}

    /**
     * 导出 Excel 数据
     *
     * @param response  HTTP 响应
     * @param fileName  文件名（不含后缀，中文自动编码）
     * @param clazz     导出 VO 类（带 @ExcelProperty 注解）
     * @param data      数据列表
     */
    public static <T> void exportExcel(HttpServletResponse response, String fileName,
                                       Class<T> clazz, List<T> data) throws IOException {
        setExcelResponse(response, fileName);
        EasyExcel.write(response.getOutputStream(), clazz).sheet(fileName).doWrite(data);
    }

    /**
     * 导出 Excel 数据（自定义 sheet 名称）
     */
    public static <T> void exportExcel(HttpServletResponse response, String fileName,
                                       String sheetName, Class<T> clazz, List<T> data) throws IOException {
        setExcelResponse(response, fileName);
        EasyExcel.write(response.getOutputStream(), clazz).sheet(sheetName).doWrite(data);
    }

    /**
     * 导出空模板
     */
    public static <T> void exportTemplate(HttpServletResponse response, String fileName,
                                           Class<T> clazz) throws IOException {
        setExcelResponse(response, fileName);
        EasyExcel.write(response.getOutputStream(), clazz).sheet(fileName).doWrite(List.of());
    }

    /**
     * 导入 Excel 数据（同步读取）
     *
     * @param inputStream 文件输入流
     * @param clazz       导入 VO 类
     * @return 数据列表
     */
    public static <T> List<T> importExcel(java.io.InputStream inputStream, Class<T> clazz) {
        return EasyExcel.read(inputStream).head(clazz).sheet().doReadSync();
    }

    /**
     * 设置 Excel 下载响应头
     */
    private static void setExcelResponse(HttpServletResponse response, String fileName) {
        response.setContentType(CONTENT_TYPE);
        response.setCharacterEncoding("utf-8");
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename=" + encoded + ".xlsx");
    }
}
