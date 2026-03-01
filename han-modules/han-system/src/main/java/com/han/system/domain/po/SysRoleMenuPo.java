package com.han.system.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 角色-菜单关联表（物理删除，无继承）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_role_menu")
public class SysRoleMenuPo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 角色ID */
    private Long roleId;

    /** 菜单ID */
    private Long menuId;
}
