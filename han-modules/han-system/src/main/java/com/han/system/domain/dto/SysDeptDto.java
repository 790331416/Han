package com.han.system.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 部门数据传输对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SysDeptDto {

    /** 部门ID（修改时必填） */
    private Long deptId;

    /** 父部门ID */
    private Long parentId;

    /** 部门名称 */
    @NotBlank(message = "部门名称不能为空")
    @Size(max = 30, message = "部门名称长度不能超过30个字符")
    private String deptName;

    /** 显示顺序 */
    private Integer sort;

    /** 负责人用户ID */
    private Long leaderId;

    /** 联系电话 */
    @Size(max = 11, message = "联系电话长度不能超过11个字符")
    private String phone;

    /** 邮箱 */
    @Size(max = 50, message = "邮箱长度不能超过50个字符")
    private String email;

    /** 状态（0正常 1停用） */
    private Integer status;
}
