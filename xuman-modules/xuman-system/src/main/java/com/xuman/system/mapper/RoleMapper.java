package com.xuman.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xuman.system.domain.entity.Role;
import com.xuman.system.domain.vo.RoleVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Set;

/**
 * 角色Mapper接口
 */
@Mapper
public interface RoleMapper extends BaseMapper<Role> {

    /**
     * 查询角色列表
     */
    List<RoleVO> selectRoleList(@Param("tenantId") Long tenantId);

    /**
     * 根据用户ID查询角色
     */
    List<RoleVO> selectRolesByUserId(@Param("userId") Long userId);

    /**
     * 查询角色菜单ID列表
     */
    Set<Long> selectMenuIdsByRoleId(@Param("roleId") Long roleId);

    /**
     * 查询角色部门ID列表(数据权限)
     */
    Set<Long> selectDeptIdsByRoleId(@Param("roleId") Long roleId);
}
