package com.han.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.han.common.core.domain.PageResult;
import com.han.common.core.exception.BusinessException;
import com.han.system.domain.dto.SysRoleDto;
import com.han.system.domain.po.SysRoleMenuPo;
import com.han.system.domain.po.SysRolePo;
import com.han.system.domain.po.SysUserRolePo;
import com.han.system.domain.query.SysRoleQuery;
import com.han.system.converter.SysRoleConverter;
import com.han.system.mapper.SysRoleMapper;
import com.han.system.mapper.SysRoleMenuMapper;
import com.han.system.mapper.SysUserRoleMapper;
import com.han.system.service.ISysRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * 角色服务实现
 */
@Service
@RequiredArgsConstructor
public class SysRoleServiceImpl implements ISysRoleService {

    private final SysRoleMapper roleMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleConverter roleConverter;

    @Override
    public PageResult<SysRolePo> selectRolePage(SysRoleQuery query) {
        Page<SysRolePo> page = roleMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()),
                buildQueryWrapper(query)
        );
        return new PageResult<>(page.getRecords(), page.getTotal());
    }

    @Override
    public List<SysRolePo> selectRoleList(SysRoleQuery query) {
        return roleMapper.selectList(buildQueryWrapper(query));
    }

    @Override
    public SysRolePo selectRoleById(Long roleId) {
        return roleMapper.selectById(roleId);
    }

    @Override
    public List<SysRolePo> selectRolesByUserId(Long userId) {
        List<SysUserRolePo> userRoles = userRoleMapper.selectList(
                new LambdaQueryWrapper<SysUserRolePo>().eq(SysUserRolePo::getUserId, userId)
        );
        if (userRoles.isEmpty()) {
            return List.of();
        }
        List<Long> roleIds = userRoles.stream().map(SysUserRolePo::getRoleId).toList();
        return roleMapper.selectList(
                new LambdaQueryWrapper<SysRolePo>()
                        .in(SysRolePo::getId, roleIds)
                        .eq(SysRolePo::getStatus, 0)
                        .orderByAsc(SysRolePo::getRoleSort)
        );
    }

    @Override
    public List<Long> selectMenuIdsByRoleId(Long roleId) {
        List<SysRoleMenuPo> roleMenus = roleMenuMapper.selectList(
                new LambdaQueryWrapper<SysRoleMenuPo>().eq(SysRoleMenuPo::getRoleId, roleId)
        );
        return roleMenus.stream().map(SysRoleMenuPo::getMenuId).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void insertRole(SysRoleDto dto) {
        validateRole(dto);

        SysRolePo role = roleConverter.toPo(dto);
        if (role.getStatus() == null) {
            role.setStatus(0);
        }
        roleMapper.insert(role);

        insertRoleMenu(role.getId(), dto.getMenuIds());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRole(SysRoleDto dto) {
        if (dto.getRoleId() == null) {
            throw new BusinessException("角色ID不能为空");
        }
        validateRole(dto);

        SysRolePo role = roleMapper.selectById(dto.getRoleId());
        if (role == null) {
            throw new BusinessException("角色不存在");
        }

        roleConverter.updatePo(dto, role);
        roleMapper.updateById(role);

        // 先删后插角色菜单
        roleMenuMapper.delete(
                new LambdaQueryWrapper<SysRoleMenuPo>().eq(SysRoleMenuPo::getRoleId, dto.getRoleId())
        );
        insertRoleMenu(dto.getRoleId(), dto.getMenuIds());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRoleById(Long roleId) {
        checkRoleAllowed(roleId);
        if (countUserByRoleId(roleId) > 0) {
            throw new BusinessException("角色已分配用户，不能删除");
        }
        roleMenuMapper.delete(
                new LambdaQueryWrapper<SysRoleMenuPo>().eq(SysRoleMenuPo::getRoleId, roleId)
        );
        roleMapper.deleteById(roleId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRoleByIds(List<Long> roleIds) {
        for (Long roleId : roleIds) {
            checkRoleAllowed(roleId);
            if (countUserByRoleId(roleId) > 0) {
                SysRolePo role = roleMapper.selectById(roleId);
                String name = role != null ? role.getRoleName() : String.valueOf(roleId);
                throw new BusinessException("角色[" + name + "]已分配用户，不能删除");
            }
        }
        roleMenuMapper.delete(
                new LambdaQueryWrapper<SysRoleMenuPo>().in(SysRoleMenuPo::getRoleId, roleIds)
        );
        roleMapper.deleteByIds(roleIds);
    }

    @Override
    public void updateRoleStatus(Long roleId, Integer status) {
        checkRoleAllowed(roleId);
        SysRolePo role = new SysRolePo();
        role.setId(roleId);
        role.setStatus(status);
        roleMapper.updateById(role);
    }

    @Override
    public long countUserByRoleId(Long roleId) {
        return userRoleMapper.selectCount(
                new LambdaQueryWrapper<SysUserRolePo>().eq(SysUserRolePo::getRoleId, roleId)
        );
    }

    @Override
    public boolean checkRoleNameUnique(String roleName, Long roleId) {
        LambdaQueryWrapper<SysRolePo> wrapper = new LambdaQueryWrapper<SysRolePo>()
                .eq(SysRolePo::getRoleName, roleName);
        if (roleId != null) {
            wrapper.ne(SysRolePo::getId, roleId);
        }
        return roleMapper.selectCount(wrapper) == 0;
    }

    @Override
    public boolean checkRoleKeyUnique(String roleKey, Long roleId) {
        LambdaQueryWrapper<SysRolePo> wrapper = new LambdaQueryWrapper<SysRolePo>()
                .eq(SysRolePo::getRoleKey, roleKey);
        if (roleId != null) {
            wrapper.ne(SysRolePo::getId, roleId);
        }
        return roleMapper.selectCount(wrapper) == 0;
    }

    // ==================== 私有方法 ====================

    private LambdaQueryWrapper<SysRolePo> buildQueryWrapper(SysRoleQuery query) {
        return new LambdaQueryWrapper<SysRolePo>()
                .like(query.getRoleName() != null && !query.getRoleName().isEmpty(),
                        SysRolePo::getRoleName, query.getRoleName())
                .like(query.getRoleKey() != null && !query.getRoleKey().isEmpty(),
                        SysRolePo::getRoleKey, query.getRoleKey())
                .eq(query.getStatus() != null, SysRolePo::getStatus, query.getStatus())
                .orderByAsc(SysRolePo::getRoleSort);
    }

    private void validateRole(SysRoleDto dto) {
        if (!checkRoleNameUnique(dto.getRoleName(), dto.getRoleId())) {
            throw new BusinessException("角色名称[" + dto.getRoleName() + "]已存在");
        }
        if (!checkRoleKeyUnique(dto.getRoleKey(), dto.getRoleId())) {
            throw new BusinessException("角色权限字符[" + dto.getRoleKey() + "]已存在");
        }
        if ("admin".equals(dto.getRoleKey()) || "tenantAdmin".equals(dto.getRoleKey())) {
            throw new BusinessException("不允许使用保留的角色标识符[" + dto.getRoleKey() + "]");
        }
    }

    private void checkRoleAllowed(Long roleId) {
        SysRolePo role = roleMapper.selectById(roleId);
        if (role != null && "admin".equals(role.getRoleKey())) {
            throw new BusinessException("不允许操作超级管理员角色");
        }
    }

    private void insertRoleMenu(Long roleId, Collection<Long> menuIds) {
        if (menuIds == null || menuIds.isEmpty()) {
            return;
        }
        for (Long menuId : menuIds) {
            roleMenuMapper.insert(new SysRoleMenuPo(roleId, menuId));
        }
    }
}
