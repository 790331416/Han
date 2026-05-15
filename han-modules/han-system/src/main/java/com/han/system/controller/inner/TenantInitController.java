package com.han.system.controller.inner;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.han.api.system.domain.TenantInitDto;
import com.han.common.core.domain.R;
import com.han.common.mybatis.helper.TenantHelper;
import com.han.common.security.annotation.InnerAuth;
import com.han.common.security.context.SecurityContextHolder;
import com.han.common.security.domain.LoginUser;
import com.han.system.domain.dto.SysUserDto;
import com.han.system.domain.po.*;
import com.han.system.mapper.*;
import com.han.system.service.ISysUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/**
 * 租户初始化 - I层（内部 RPC 接口）
 * <p>
 * 由 han-tenant 模块在创建/同步租户时调用，负责初始化租户的管理员用户、默认角色、默认部门，以及同步角色菜单。
 */
@Slf4j
@InnerAuth
@RestController("innerTenantInitController")
@RequestMapping("/inner/system/tenant")
@RequiredArgsConstructor
public class TenantInitController {

    private final ISysUserService sysUserService;
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysDeptMapper deptMapper;
    private final SysRoleMenuMapper roleMenuMapper;

    @PostMapping("/init")
    @Transactional(rollbackFor = Exception.class)
    public R<Void> initTenantData(@RequestBody TenantInitDto dto) {
        Long tenantId = dto.getTenantId();
        log.info("开始初始化租户[{}]基础数据", tenantId);

        // Phase 1: 创建部门、角色、菜单权限（直接操作Mapper，跳过租户过滤）
        final Long[] ids = new Long[2]; // [0]=deptId, [1]=roleId
        TenantHelper.ignore(() -> {
            // 1. 创建默认部门（根部门）
            SysDeptPo dept = new SysDeptPo();
            dept.setTenantId(tenantId);
            dept.setDeptName(dto.getTenantName());
            dept.setParentId(0L);
            dept.setAncestors("0");
            dept.setOrderNum(0);
            dept.setStatus(0);
            deptMapper.insert(dept);
            ids[0] = dept.getId();
            log.info("租户[{}]创建默认部门: deptId={}", tenantId, ids[0]);

            // 2. 创建默认角色（租户管理员）
            SysRolePo role = new SysRolePo();
            role.setTenantId(tenantId);
            role.setRoleName("租户管理员");
            role.setRoleKey("tenantAdmin");
            role.setRoleSort(1);
            role.setDataScope("1");
            role.setStatus(0);
            roleMapper.insert(role);
            ids[1] = role.getId();
            log.info("租户[{}]创建默认角色: roleId={}", tenantId, ids[1]);

            // 3. 分配套餐菜单给角色
            Set<Long> menuIds = dto.getMenuIds();
            if (menuIds != null && !menuIds.isEmpty()) {
                for (Long menuId : menuIds) {
                    roleMenuMapper.insert(new SysRoleMenuPo(ids[1], menuId));
                }
                log.info("租户[{}]分配菜单权限: menuCount={}", tenantId, menuIds.size());
            }
        });

        // Phase 2: 通过 ISysUserService 创建管理员用户（复用用户管理的校验逻辑）
        LoginUser tempUser = LoginUser.builder()
                .userId(1L)
                .tenantId(tenantId)
                .username("system-init")
                .build();
        SecurityContextHolder.setLoginUser(tempUser);
        try {
            SysUserDto userDto = SysUserDto.builder()
                    .username(dto.getAdminUsername() != null ? dto.getAdminUsername() : "admin")
                    .password(dto.getAdminPassword() != null ? dto.getAdminPassword() : "admin123")
                    .nickname(dto.getAdminNickname() != null ? dto.getAdminNickname() : "租户管理员")
                    .phone(dto.getAdminPhone())
                    .deptId(ids[0])
                    .roleIds(Set.of(ids[1]))
                    .status(0)
                    .build();
            sysUserService.insert(userDto);
            log.info("租户[{}]通过用户服务创建管理员用户: username={}", tenantId, userDto.getUsername());
        } finally {
            SecurityContextHolder.clear();
        }

        log.info("租户[{}]基础数据初始化完成", tenantId);
        return R.ok();
    }

    /**
     * 查询租户管理员用户ID（供重置密码等操作使用）
     */
    public R<Long> getTenantAdminUserId(@RequestParam("tenantId") Long tenantId) {
        SysUserPo admin = TenantHelper.ignore(() ->
                userMapper.selectOne(
                        new LambdaQueryWrapper<SysUserPo>()
                                .eq(SysUserPo::getTenantId, tenantId)
                                .orderByAsc(SysUserPo::getId)
                                .last("LIMIT 1")
                )
        );
        if (admin == null) {
            return R.fail("该租户下无用户");
        }
        return R.ok(admin.getId());
    }

    /**
     * 同步租户角色菜单权限（套餐变更时调用）
     * <p>
     * 将租户下所有角色的菜单权限替换为新的菜单ID集合。
     */
    @PostMapping("/syncRoleMenus")
    @Transactional(rollbackFor = Exception.class)
    public R<Void> syncRoleMenus(@RequestParam Long tenantId, @RequestBody Set<Long> menuIds) {
        log.info("开始同步租户[{}]角色菜单, menuCount={}", tenantId, menuIds.size());

        TenantHelper.ignore(() -> {
            // 1. 查出该租户下所有角色
            List<SysRolePo> roles = roleMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysRolePo>()
                            .eq(SysRolePo::getTenantId, tenantId)
                            .select(SysRolePo::getId)
            );

            for (SysRolePo role : roles) {
                // 2. 删除旧的角色-菜单关联
                roleMenuMapper.delete(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysRoleMenuPo>()
                                .eq(SysRoleMenuPo::getRoleId, role.getId())
                );

                // 3. 插入新的角色-菜单关联
                if (menuIds != null && !menuIds.isEmpty()) {
                    for (Long menuId : menuIds) {
                        roleMenuMapper.insert(new SysRoleMenuPo(role.getId(), menuId));
                    }
                }
            }

            log.info("租户[{}]角色菜单同步完成, roleCount={}, menuCount={}", tenantId, roles.size(), menuIds.size());
        });

        return R.ok();
    }
}
