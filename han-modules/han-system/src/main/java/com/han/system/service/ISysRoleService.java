package com.han.system.service;

import com.han.common.core.domain.PageResult;
import com.han.system.domain.dto.SysRoleDto;
import com.han.system.domain.po.SysRolePo;
import com.han.system.domain.po.SysUserPo;
import com.han.system.domain.query.SysRoleQuery;

import java.util.List;

/**
 * 角色服务接口
 */
public interface ISysRoleService {

    /**
     * 分页查询角色列表
     */
    PageResult<SysRolePo> selectRolePage(SysRoleQuery query);

    /**
     * 查询角色列表（不分页）
     */
    List<SysRolePo> selectRoleList(SysRoleQuery query);

    /**
     * 根据ID查询角色
     */
    SysRolePo selectRoleById(Long roleId);

    /**
     * 查询用户的角色列表
     */
    List<SysRolePo> selectRolesByUserId(Long userId);

    /**
     * 查询角色关联的菜单ID列表
     */
    List<Long> selectMenuIdsByRoleId(Long roleId);

    /**
     * 新增角色（含角色菜单关联）
     */
    void insertRole(SysRoleDto dto);

    /**
     * 修改角色（含角色菜单关联）
     */
    void updateRole(SysRoleDto dto);

    /**
     * 删除角色（含关联清理）
     */
    void deleteRoleById(Long roleId);

    /**
     * 批量删除角色
     */
    void deleteRoleByIds(List<Long> roleIds);

    /**
     * 修改角色状态
     */
    void updateRoleStatus(Long roleId, Integer status);

    /**
     * 查询角色下用户数
     */
    long countUserByRoleId(Long roleId);

    /**
     * 校验角色名称唯一
     */
    boolean checkRoleNameUnique(String roleName, Long roleId);

    /**
     * 校验角色权限字符唯一
     */
    boolean checkRoleKeyUnique(String roleKey, Long roleId);

    /**
     * 查询角色已分配的用户列表（分页）
     */
    PageResult<SysUserPo> selectAllocatedUsers(Long roleId, String username, String phone, Integer pageNum, Integer pageSize);

    /**
     * 查询角色未分配的用户列表（分页）
     */
    PageResult<SysUserPo> selectUnallocatedUsers(Long roleId, String username, String phone, Integer pageNum, Integer pageSize);

    /**
     * 批量授权用户
     */
    void authUsers(Long roleId, List<Long> userIds);

    /**
     * 批量取消授权用户
     */
    void cancelAuthUsers(Long roleId, List<Long> userIds);
}
