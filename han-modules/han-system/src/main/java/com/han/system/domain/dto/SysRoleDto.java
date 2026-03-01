package com.han.system.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * 角色数据传输对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SysRoleDto {

    /** 角色ID（修改时必填） */
    private Long roleId;

    /** 角色名称 */
    @NotBlank(message = "角色名称不能为空")
    @Size(max = 30, message = "角色名称长度不能超过30个字符")
    private String roleName;

    /** 角色权限字符串 */
    @NotBlank(message = "权限字符不能为空")
    @Size(max = 100, message = "权限字符长度不能超过100个字符")
    private String roleKey;

    /** 显示顺序 */
    private Integer roleSort;

    /** 数据范围 */
    private String dataScope;

    /** 菜单树选择项是否关联显示 */
    private Integer menuCheckStrictly;

    /** 部门树选择项是否关联显示 */
    private Integer deptCheckStrictly;

    /** 状态（0正常 1停用） */
    private Integer status;

    /** 备注 */
    private String remark;

    /** 菜单ID列表 */
    private Set<Long> menuIds;
}
