package com.han.system.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 用户-角色关联表（物理删除，无继承）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_user_role")
public class SysUserRolePo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户ID */
    private Long userId;

    /** 角色ID */
    private Long roleId;
}
