package com.han.system.sdfz.education.domain;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/** 人员全字段导入模板；关联对象使用中文名称，密码只用于建号，不会写入日志或结果。 */
@Data
public class EducationPersonImportVo {

    @ExcelProperty("学校（必填）")
    private String schoolName;
    @ExcelProperty("姓名（必填）")
    private String personName;
    @ExcelProperty("人员类型（必填）")
    private String personType;
    @ExcelProperty("校内岗位（教师选填）")
    private String dutyName;
    @ExcelProperty("手机号（必填）")
    private String phone;
    @ExcelProperty("状态（必填）")
    private String status;
    @ExcelProperty("备注（选填）")
    private String remark;
    @ExcelProperty("离校状态（选填）")
    private String leaveFlag;
    @ExcelProperty("启用校端登录（必填）")
    private String loginEnabled;
    @ExcelProperty("用户名（选填）")
    private String username;
    @ExcelProperty("校端初始密码（启用登录必填）")
    private String password;
    @ExcelProperty("系统管理权限（校级管理员选填）")
    private String roleNames;
    @ExcelProperty("所属年级（学生必填/教师选填）")
    private String gradeNames;
    @ExcelProperty("所属班级（学生必填/教师选填）")
    private String classNames;
    @ExcelProperty("任教科目（教师选填）")
    private String subjectNames;
}
