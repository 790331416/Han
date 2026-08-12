package com.han.common.web.excel;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.read.listener.ReadListener;
import jakarta.servlet.http.HttpServletResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
 *
 * <p>导出会先写入内存缓冲、成功后再提交响应，并统一挂载
 * {@link FormulaInjectionCellWriteHandler} 做公式注入防护。
 * 输入流的关闭由调用方负责，建议用 try-with-resources。
 */
public final class ExcelUtil {

    private static final String CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /** POI 对 sheet 名的硬限制 */
    private static final int MAX_SHEET_NAME_LENGTH = 31;
    private static final String ILLEGAL_SHEET_NAME_CHARS = "[:\\\\/?*\\[\\]]";

    /** 导入行数默认上限，避免一次把整个 sheet 反序列化进内存 */
    public static final int DEFAULT_MAX_IMPORT_ROWS = 50_000;

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
        exportExcel(response, fileName, fileName, clazz, data);
    }

    /**
     * 导出 Excel 数据（自定义 sheet 名称）
     */
    public static <T> void exportExcel(HttpServletResponse response, String fileName,
                                       String sheetName, Class<T> clazz, List<T> data) throws IOException {
        writeAndCommit(response, fileName, sheetName, clazz, data);
    }

    /**
     * 导出空模板
     */
    public static <T> void exportTemplate(HttpServletResponse response, String fileName,
                                           Class<T> clazz) throws IOException {
        writeAndCommit(response, fileName, fileName, clazz, List.of());
    }

    /**
     * 导入 Excel 数据（行数上限取 {@link #DEFAULT_MAX_IMPORT_ROWS}）
     *
     * @param inputStream 文件输入流，由调用方负责关闭
     * @param clazz       导入 VO 类
     * @return 数据列表
     */
    public static <T> List<T> importExcel(InputStream inputStream, Class<T> clazz) {
        return importExcel(inputStream, clazz, DEFAULT_MAX_IMPORT_ROWS);
    }

    /**
     * 导入 Excel 数据（指定行数上限）
     * <p>原实现用 {@code doReadSync()} 把整个 sheet 一次性反序列化成对象再返回，
     * 几万行的导入文件足以在 Web 线程上触发 OOM，且没有任何上限。
     *
     * @throws IllegalArgumentException 行数超过 {@code maxRows}
     */
    public static <T> List<T> importExcel(InputStream inputStream, Class<T> clazz, int maxRows) {
        List<T> rows = new ArrayList<>();
        EasyExcel.read(inputStream, clazz, new ReadListener<T>() {
            @Override
            public void invoke(T data, AnalysisContext context) {
                if (rows.size() >= maxRows) {
                    throw new IllegalArgumentException("导入行数超过上限 " + maxRows + " 行，请拆分文件后重试");
                }
                rows.add(data);
            }

            @Override
            public void doAfterAllAnalysed(AnalysisContext context) {
                // 数据已收集在 rows 中
            }
        }).sheet().doRead();
        return rows;
    }

    /**
     * 先写内存缓冲、成功后再提交响应。
     * <p>原实现是设好响应头就直接往 {@code response.getOutputStream()} 写，
     * 一旦写入过程中抛异常（数据转换失败、sheet 名非法、下游查询超时），
     * 响应头已发出、部分字节已写出，全局异常处理再想返回 JSON 已经不可能，
     * 客户端只会拿到一个损坏的 xlsx 且看不到任何错误信息。
     */
    private static <T> void writeAndCommit(HttpServletResponse response, String fileName,
                                           String sheetName, Class<T> clazz, List<T> data) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        EasyExcel.write(buffer, clazz)
                .registerWriteHandler(new FormulaInjectionCellWriteHandler())
                .sheet(sanitizeSheetName(sheetName))
                .doWrite(data);

        setExcelResponse(response, fileName);
        response.setContentLength(buffer.size());
        OutputStream out = response.getOutputStream();
        buffer.writeTo(out);
        out.flush();
    }

    /**
     * sheet 名做长度截断与非法字符替换。
     * <p>超过 31 字符或含 {@code : \ / ? * [ ]} 时 POI 会抛异常，
     * 而此时正好落进「响应已提交」那个场景。
     */
    static String sanitizeSheetName(String sheetName) {
        if (sheetName == null || sheetName.isBlank()) {
            return "Sheet1";
        }
        String safe = sheetName.replaceAll(ILLEGAL_SHEET_NAME_CHARS, "_");
        return safe.length() > MAX_SHEET_NAME_LENGTH ? safe.substring(0, MAX_SHEET_NAME_LENGTH) : safe;
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
