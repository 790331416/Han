package com.han.system.domain.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import com.han.common.web.sensitive.Sensitive;
import com.han.common.web.sensitive.SensitiveType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户导出 VO（EasyExcel）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserExportVo {

    @ExcelProperty("用户ID")
    @ColumnWidth(25)
    private String userId;

    @ExcelProperty("用户名")
    @ColumnWidth(15)
    private String username;

    @ExcelProperty("昵称")
    @ColumnWidth(15)
    private String nickname;

    @ExcelProperty("部门")
    @ColumnWidth(20)
    private String deptName;

    @ExcelProperty("手机号")
    @ColumnWidth(15)
    @Sensitive(SensitiveType.PHONE)
    private String phone;

    @ExcelProperty("邮箱")
    @ColumnWidth(25)
    @Sensitive(SensitiveType.EMAIL)
    private String email;

    @ExcelProperty("性别")
    @ColumnWidth(8)
    private String sexText;

    @ExcelProperty("状态")
    @ColumnWidth(8)
    private String statusText;

    @ExcelProperty("创建时间")
    @ColumnWidth(20)
    private String createTime;
}
