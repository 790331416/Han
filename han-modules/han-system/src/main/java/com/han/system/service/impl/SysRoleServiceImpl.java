package com.han.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.han.common.core.domain.PageResult;
import com.han.common.core.exception.BusinessException;
import com.han.common.core.exception.ForbiddenException;
import com.han.common.security.context.SecurityContextHolder;
import com.han.common.security.util.DataOwnerUtil;
import com.han.system.domain.dto.SysRoleDto;
import com.han.system.domain.po.SysRoleMenuPo;
import com.han.system.domain.po.SysRolePo;
import com.han.system.domain.po.SysUserPo;
import com.han.system.domain.po.SysUserRolePo;
import com.han.system.domain.query.SysRoleQuery;
import com.han.system.converter.SysRoleConverter;
import com.han.system.mapper.SysRoleMapper;
import com.han.system.mapper.SysRoleMenuMapper;
import com.han.system.mapper.SysUserMapper;
import com.han.system.mapper.SysUserRoleMapper;
import com.han.system.service.ISysMenuService;
import com.han.system.service.ISysRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 角色服务实现
 */
@Service
@RequiredArgsConstructor
public class SysRoleServiceImpl implements ISysRoleService {

    /** 用户表上的凭据字段，任何角色侧查询都不得带出 */
    private static final Set<String> CREDENTIAL_FIELDS = Set.of("password", "totpSecret");

    private final SysRoleMapper roleMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysUserMapper userMapper;
    private final SysRoleConverter roleConverter;
    private final ISysMenuService menuService;

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
        checkMenuGrantAllowed(dto.getMenuIds());

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
        checkRoleAllowed(dto.getRoleId());
        validateRole(dto);
        checkMenuGrantAllowed(dto.getMenuIds());

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

    @Override
    public PageResult<SysUserPo> selectAllocatedUsers(Long roleId, String username, String phone, Integer pageNum, Integer pageSize) {
        // 查出该角色已关联的用户ID
        List<SysUserRolePo> userRoles = userRoleMapper.selectList(
                new LambdaQueryWrapper<SysUserRolePo>().eq(SysUserRolePo::getRoleId, roleId)
        );
        if (userRoles.isEmpty()) {
            return new PageResult<>(List.of(), 0L);
        }
        List<Long> userIds = userRoles.stream().map(SysUserRolePo::getUserId).toList();

        LambdaQueryWrapper<SysUserPo> wrapper = new LambdaQueryWrapper<SysUserPo>()
                .in(SysUserPo::getId, userIds)
                .like(username != null && !username.isEmpty(), SysUserPo::getUsername, username)
                .like(phone != null && !phone.isEmpty(), SysUserPo::getPhone, phone);
        excludeCredentialColumns(wrapper);

        Page<SysUserPo> page = userMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return new PageResult<>(page.getRecords(), page.getTotal());
    }

    @Override
    public PageResult<SysUserPo> selectUnallocatedUsers(Long roleId, String username, String phone, Integer pageNum, Integer pageSize) {
        // 查出该角色已关联的用户ID
        List<SysUserRolePo> userRoles = userRoleMapper.selectList(
                new LambdaQueryWrapper<SysUserRolePo>().eq(SysUserRolePo::getRoleId, roleId)
        );
        List<Long> allocatedUserIds = userRoles.stream().map(SysUserRolePo::getUserId).toList();

        LambdaQueryWrapper<SysUserPo> wrapper = new LambdaQueryWrapper<SysUserPo>()
                .eq(SysUserPo::getStatus, 0)
                .like(username != null && !username.isEmpty(), SysUserPo::getUsername, username)
                .like(phone != null && !phone.isEmpty(), SysUserPo::getPhone, phone);
        if (!allocatedUserIds.isEmpty()) {
            wrapper.notIn(SysUserPo::getId, allocatedUserIds);
        }
        excludeCredentialColumns(wrapper);

        Page<SysUserPo> page = userMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return new PageResult<>(page.getRecords(), page.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void authUsers(Long roleId, List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        checkRoleAllowed(roleId);
        DataOwnerUtil.checkRolePermission(Set.of(roleId));
        checkUsersInCurrentTenant(userIds);
        for (Long userId : userIds) {
            // 防重复
            Long count = userRoleMapper.selectCount(
                    new LambdaQueryWrapper<SysUserRolePo>()
                            .eq(SysUserRolePo::getRoleId, roleId)
                            .eq(SysUserRolePo::getUserId, userId)
            );
            if (count == 0) {
                userRoleMapper.insert(new SysUserRolePo(userId, roleId));
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelAuthUsers(Long roleId, List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        checkRoleAllowed(roleId);
        DataOwnerUtil.checkRolePermission(Set.of(roleId));
        checkUsersInCurrentTenant(userIds);
        userRoleMapper.delete(
                new LambdaQueryWrapper<SysUserRolePo>()
                        .eq(SysUserRolePo::getRoleId, roleId)
                        .in(SysUserRolePo::getUserId, userIds)
        );
    }

    // ==================== 私有方法 ====================

    /**
     * 「分配用户」两个列表直出 {@link SysUserPo}，这里在 SQL 层就不查密码哈希与 TOTP 密钥，
     * 与 PO 上的 {@code WRITE_ONLY} 构成双重防护，同时保持响应字段结构不变。
     */
    private void excludeCredentialColumns(LambdaQueryWrapper<SysUserPo> wrapper) {
        wrapper.select(SysUserPo.class,
                field -> !CREDENTIAL_FIELDS.contains(field.getProperty()));
    }

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
            if (dto.getRoleId() == null) {
                throw new BusinessException("不允许使用保留的角色标识符[" + dto.getRoleKey() + "]");
            }
            SysRolePo existing = roleMapper.selectById(dto.getRoleId());
            if (existing == null || !dto.getRoleKey().equals(existing.getRoleKey())) {
                throw new BusinessException("不允许使用保留的角色标识符[" + dto.getRoleKey() + "]");
            }
        }
    }

    /**
     * 超管角色不可被任何入口改写；角色不存在时也要拦下，
     * 否则跨租户传一个不可见的 roleId 会被静默放行并写出脏关联。
     */
    private void checkRoleAllowed(Long roleId) {
        if (roleId == null) {
            throw new BusinessException("角色ID不能为空");
        }
        SysRolePo role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException("角色不存在");
        }
        if ("admin".equals(role.getRoleKey())) {
            throw new BusinessException("不允许操作超级管理员角色");
        }
    }

    /**
     * 菜单集合必须是操作者自身菜单的子集，否则「新建角色 + 自我授权」两步就能提权到任意权限点。
     *
     * <p>超级管理员拥有全量菜单，无需校验。
     */
    private void checkMenuGrantAllowed(Collection<Long> menuIds) {
        if (menuIds == null || menuIds.isEmpty() || SecurityContextHolder.isAdmin()) {
            return;
        }
        Long operatorId = SecurityContextHolder.getUserId();
        if (operatorId == null) {
            throw new ForbiddenException("未登录");
        }
        Set<Long> ownedMenuIds = new HashSet<>(menuService.selectMenuIdsByUserId(operatorId));
        for (Long menuId : menuIds) {
            if (!ownedMenuIds.contains(menuId)) {
                throw new ForbiddenException("无权分配未拥有的菜单权限: " + menuId);
            }
        }
    }

    /**
     * 关联表 sys_user_role 被排除出租户过滤，跨租户绑定只能在应用层拦。
     * 主表 sys_user 会被租户插件加条件，查不齐即说明有 ID 不属于当前租户。
     */
    private void checkUsersInCurrentTenant(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty() || SecurityContextHolder.isAdmin()) {
            return;
        }
        Set<Long> distinctIds = new HashSet<>(userIds);
        Long visible = userMapper.selectCount(
                new LambdaQueryWrapper<SysUserPo>().in(SysUserPo::getId, distinctIds)
        );
        if (visible == null || visible != distinctIds.size()) {
            throw new ForbiddenException("存在不属于当前租户的用户，无法授权");
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
