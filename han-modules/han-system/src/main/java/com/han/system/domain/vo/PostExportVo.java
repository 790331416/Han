package com.han.system.domain.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 岗位导出 VO（EasyExcel）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostExportVo {

    @ExcelProperty("岗位ID")
    @ColumnWidth(25)
    private String postId;

    @ExcelProperty("岗位编码")
    @ColumnWidth(15)
    private String postCode;

    @ExcelProperty("岗位名称")
    @ColumnWidth(15)
    private String postName;

    @ExcelProperty("排序")
    @ColumnWidth(8)
    private String postSort;

    @ExcelProperty("状态")
    @ColumnWidth(8)
    private String statusText;

    @ExcelProperty("创建时间")
    @ColumnWidth(20)
    private String createTime;
}
