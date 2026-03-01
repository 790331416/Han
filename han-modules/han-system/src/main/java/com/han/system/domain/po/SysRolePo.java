package com.han.system.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.han.common.mybatis.domain.entity.BizEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 角色持久化对象
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role")
public class SysRolePo extends BizEntity {

    /** 角色名称 */
    private String roleName;

    /** 角色权限字符串 */
    private String roleKey;

    /** 显示顺序 */
    private Integer roleSort;

    /** 数据范围(1全部 2自定义 3本部门 4本部门及以下 5仅本人) */
    private String dataScope;

    /** 菜单树选择项是否关联显示 */
    private Integer menuCheckStrictly;

    /** 部门树选择项是否关联显示 */
    private Integer deptCheckStrictly;

    /** 状态(0正常 1停用) */
    private Integer status;
}
