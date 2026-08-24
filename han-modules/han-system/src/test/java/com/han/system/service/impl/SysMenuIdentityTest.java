package com.han.system.service.impl;

import com.han.api.tenant.TenantServiceClient;
import com.han.common.security.context.SecurityContextHolder;
import com.han.common.security.domain.LoginUser;
import com.han.system.converter.SysUserConverter;
import com.han.system.domain.po.SysMenuPo;
import com.han.system.domain.po.SysRoleMenuPo;
import com.han.system.domain.po.SysUserRolePo;
import com.han.system.mapper.SysMenuMapper;
import com.han.system.mapper.SysRoleMapper;
import com.han.system.mapper.SysRoleMenuMapper;
import com.han.system.mapper.SysUserMapper;
import com.han.system.mapper.SysUserPostMapper;
import com.han.system.mapper.SysUserRoleMapper;
import com.han.system.sdfz.education.EducationAccountIdentityService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 「一账号、多学校身份、按身份隔离」菜单与权限过滤测试。
 *
 * <p>覆盖：系统账号原逻辑、SCHOOL_ADMIN 保留管理菜单/权限、TEACHER 空菜单且不继承管理角色。
 */
class SysMenuIdentityTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clear();
    }

    // ==================== 菜单过滤（SysMenuServiceImpl） ====================

    @Test
    void systemAccountKeepsAllRoleMenusWhenNotIdentityScoped() {
        SysMenuMapper menuMapper = mock(SysMenuMapper.class);
        SysRoleMenuMapper roleMenuMapper = mock(SysRoleMenuMapper.class);
        SysUserRoleMapper userRoleMapper = mock(SysUserRoleMapper.class);
        SysUserMapper sysUserMapper = mock(SysUserMapper.class);
        SysMenuServiceImpl service = new SysMenuServiceImpl(menuMapper, roleMenuMapper, userRoleMapper, sysUserMapper);

        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(2L).tenantId(1L).build());

        when(userRoleMapper.selectList(any())).thenReturn(List.of(userRole(2L, 1L), userRole(2L, 2L)));
        when(roleMenuMapper.selectList(any())).thenReturn(List.of(roleMenu(1L, 10L), roleMenu(2L, 20L)));
        when(menuMapper.selectList(any())).thenReturn(List.of(
                menu(1L, 0L, "系统管理", "M"),
                menu(10L, 1L, "管理菜单", "C"),
                menu(20L, 1L, "教师菜单", "C")));

        List<SysMenuPo> menus = service.selectMenuTreeByUserId(2L);

        assertThat(menus).hasSize(1);
        assertThat(menus.get(0).getChildren())
                .extracting(SysMenuPo::getMenuName)
                .containsExactlyInAnyOrder("管理菜单", "教师菜单");
    }

    @Test
    void schoolAdminKeepsOnlyManagementMenus() {
        SysMenuMapper menuMapper = mock(SysMenuMapper.class);
        SysRoleMenuMapper roleMenuMapper = mock(SysRoleMenuMapper.class);
        SysUserRoleMapper userRoleMapper = mock(SysUserRoleMapper.class);
        SysUserMapper sysUserMapper = mock(SysUserMapper.class);
        SysMenuServiceImpl service = new SysMenuServiceImpl(menuMapper, roleMenuMapper, userRoleMapper, sysUserMapper);

        SecurityContextHolder.setLoginUser(LoginUser.builder()
                .userId(2L).tenantId(1L).identityScoped(true).dutyCode("SCHOOL_ADMIN").build());

        when(sysUserMapper.selectManagementRoleIdsByUserId(2L)).thenReturn(Set.of(1L));
        when(roleMenuMapper.selectList(any())).thenReturn(List.of(roleMenu(1L, 10L)));
        when(menuMapper.selectList(any())).thenReturn(List.of(
                menu(1L, 0L, "系统管理", "M"),
                menu(10L, 1L, "管理菜单", "C"),
                menu(20L, 1L, "教师菜单", "C")));

        List<SysMenuPo> menus = service.selectMenuTreeByUserId(2L);

        assertThat(menus).hasSize(1);
        assertThat(menus.get(0).getChildren())
                .extracting(SysMenuPo::getMenuName)
                .containsExactly("管理菜单");
        verify(userRoleMapper, never()).selectList(any());
    }

    @Test
    void teacherGetsEmptyMenus() {
        SysMenuMapper menuMapper = mock(SysMenuMapper.class);
        SysRoleMenuMapper roleMenuMapper = mock(SysRoleMenuMapper.class);
        SysUserRoleMapper userRoleMapper = mock(SysUserRoleMapper.class);
        SysUserMapper sysUserMapper = mock(SysUserMapper.class);
        SysMenuServiceImpl service = new SysMenuServiceImpl(menuMapper, roleMenuMapper, userRoleMapper, sysUserMapper);

        SecurityContextHolder.setLoginUser(LoginUser.builder()
                .userId(2L).tenantId(1L).identityScoped(true).dutyCode("TEACHER").build());

        assertThat(service.selectMenuTreeByUserId(2L)).isEmpty();
        verify(userRoleMapper, never()).selectList(any());
        verify(roleMenuMapper, never()).selectList(any());
        verify(menuMapper, never()).selectList(any());
        verify(sysUserMapper, never()).selectManagementRoleIdsByUserId(2L);
    }

    // ==================== 权限/角色过滤（SysUserServiceImpl） ====================

    @Test
    void schoolAdminKeepsManagementPermissionsAndRoleKeys() {
        SysUserMapper sysUserMapper = mock(SysUserMapper.class);
        SysUserServiceImpl service = buildUserService(sysUserMapper);

        SecurityContextHolder.setLoginUser(LoginUser.builder()
                .userId(2L).tenantId(1L).identityScoped(true).dutyCode("SCHOOL_ADMIN").build());

        when(sysUserMapper.selectManagementRoleIdsByUserId(2L)).thenReturn(Set.of(1L));
        when(sysUserMapper.selectPermissionsByRoleIds(Set.of(1L))).thenReturn(Set.of("system:user:list"));
        when(sysUserMapper.selectRoleKeysByRoleIds(Set.of(1L))).thenReturn(Set.of("common"));

        assertThat(service.selectPermissionsByUserId(2L)).containsExactly("system:user:list");
        assertThat(service.selectRoleKeysByUserId(2L)).containsExactly("common");
        verify(sysUserMapper, never()).selectPermissionsByUserId(2L);
        verify(sysUserMapper, never()).selectRoleKeysByUserId(2L);
    }

    @Test
    void teacherDoesNotInheritAccountRolesOrPermissions() {
        SysUserMapper sysUserMapper = mock(SysUserMapper.class);
        SysUserServiceImpl service = buildUserService(sysUserMapper);

        SecurityContextHolder.setLoginUser(LoginUser.builder()
                .userId(2L).tenantId(1L).identityScoped(true).dutyCode("TEACHER").build());

        assertThat(service.selectPermissionsByUserId(2L)).isEmpty();
        assertThat(service.selectRoleKeysByUserId(2L)).isEmpty();
        verify(sysUserMapper, never()).selectManagementRoleIdsByUserId(2L);
        verify(sysUserMapper, never()).selectPermissionsByUserId(2L);
        verify(sysUserMapper, never()).selectRoleKeysByUserId(2L);
    }

    private SysUserServiceImpl buildUserService(SysUserMapper sysUserMapper) {
        return new SysUserServiceImpl(
                sysUserMapper,
                mock(SysUserConverter.class),
                mock(SysUserRoleMapper.class),
                mock(SysUserPostMapper.class),
                mock(SysRoleMapper.class),
                mock(TenantServiceClient.class),
                mock(EducationAccountIdentityService.class));
    }

    private static SysUserRolePo userRole(Long userId, Long roleId) {
        SysUserRolePo po = new SysUserRolePo();
        po.setUserId(userId);
        po.setRoleId(roleId);
        return po;
    }

    private static SysRoleMenuPo roleMenu(Long roleId, Long menuId) {
        SysRoleMenuPo po = new SysRoleMenuPo();
        po.setRoleId(roleId);
        po.setMenuId(menuId);
        return po;
    }

    private static SysMenuPo menu(Long id, Long parentId, String name, String type) {
        SysMenuPo menu = new SysMenuPo();
        menu.setId(id);
        menu.setParentId(parentId);
        menu.setMenuName(name);
        menu.setMenuType(type);
        menu.setStatus(0);
        return menu;
    }
}
