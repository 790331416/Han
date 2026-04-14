package com.han.system.domain.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录日志导出 VO（EasyExcel）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginLogExportVo {

    @ExcelProperty("日志ID")
    @ColumnWidth(25)
    private String id;

    @ExcelProperty("用户名")
    @ColumnWidth(15)
    private String username;

    @ExcelProperty("登录IP")
    @ColumnWidth(16)
    private String ipAddr;

    @ExcelProperty("归属地")
    @ColumnWidth(16)
    private String loginLocation;

    @ExcelProperty("状态")
    @ColumnWidth(8)
    private String statusText;

    @ExcelProperty("消息")
    @ColumnWidth(20)
    private String message;

    @ExcelProperty("浏览器")
    @ColumnWidth(12)
    private String browser;

    @ExcelProperty("操作系统")
    @ColumnWidth(12)
    private String os;

    @ExcelProperty("登录时间")
    @ColumnWidth(20)
    private String loginTime;
}
