package com.han.system.domain.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 操作日志导出 VO（EasyExcel）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperLogExportVo {

    @ExcelProperty("日志ID")
    @ColumnWidth(25)
    private String id;

    @ExcelProperty("模块")
    @ColumnWidth(15)
    private String module;

    @ExcelProperty("操作类型")
    @ColumnWidth(12)
    private String operTypeText;

    @ExcelProperty("操作人员")
    @ColumnWidth(12)
    private String operName;

    @ExcelProperty("操作IP")
    @ColumnWidth(16)
    private String operIp;

    @ExcelProperty("归属地")
    @ColumnWidth(16)
    private String operLocation;

    @ExcelProperty("请求方式")
    @ColumnWidth(10)
    private String requestMethod;

    @ExcelProperty("状态")
    @ColumnWidth(8)
    private String statusText;

    @ExcelProperty("耗时(ms)")
    @ColumnWidth(10)
    private String costTime;

    @ExcelProperty("操作时间")
    @ColumnWidth(20)
    private String operTime;
}
