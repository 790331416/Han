package com.han.system.domain.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 角色导出 VO（EasyExcel）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleExportVo {

    @ExcelProperty("角色ID")
    @ColumnWidth(25)
    private String roleId;

    @ExcelProperty("角色名称")
    @ColumnWidth(15)
    private String roleName;

    @ExcelProperty("权限字符")
    @ColumnWidth(20)
    private String roleKey;

    @ExcelProperty("排序")
    @ColumnWidth(8)
    private String roleSort;

    @ExcelProperty("状态")
    @ColumnWidth(8)
    private String statusText;

    @ExcelProperty("创建时间")
    @ColumnWidth(20)
    private String createTime;
}
