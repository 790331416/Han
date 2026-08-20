package com.han.system.sdfz.education.domain;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/** 人员全字段导入模板；关联对象使用中文名称，密码只用于建号，不会写入日志或结果。 */
@Data
public class EducationPersonImportVo {

    @ExcelProperty("学校")
    private String schoolName;
    @ExcelProperty("姓名")
    private String personName;
    @ExcelProperty("人员类型")
    private String personType;
    @ExcelProperty("校内岗位")
    private String dutyName;
    @ExcelProperty("手机号")
    private String phone;
    @ExcelProperty("状态")
    private String status;
    @ExcelProperty("备注")
    private String remark;
    @ExcelProperty("离校状态")
    private String leaveFlag;
    @ExcelProperty("启用校端登录")
    private String loginEnabled;
    @ExcelProperty("用户名")
    private String username;
    @ExcelProperty("校端初始密码")
    private String password;
    @ExcelProperty("系统管理权限")
    private String roleNames;
    @ExcelProperty("清除管理端角色")
    private String clearRoles;
    @ExcelProperty("所属班级")
    private String classNames;
    @ExcelProperty("归班角色")
    private String membershipRole;
    @ExcelProperty("任教科目")
    private String subjectNames;
}
