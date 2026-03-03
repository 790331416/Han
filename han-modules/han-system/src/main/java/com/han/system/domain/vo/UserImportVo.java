package com.han.system.domain.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * 用户导入 VO（EasyExcel）
 */
@Data
public class UserImportVo {

    @ExcelProperty("用户名")
    private String username;

    @ExcelProperty("昵称")
    private String nickname;

    @ExcelProperty("密码")
    private String password;

    @ExcelProperty("手机号")
    private String phone;

    @ExcelProperty("邮箱")
    private String email;

    @ExcelProperty("性别")
    private String sexText;

    @ExcelProperty("部门名称")
    private String deptName;

    @ExcelProperty("岗位编码")
    private String postCode;

    @ExcelProperty("角色名称")
    private String roleName;
}
