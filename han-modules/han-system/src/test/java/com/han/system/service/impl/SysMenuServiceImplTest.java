package com.han.system.service.impl;

import com.han.system.domain.po.SysMenuPo;
import com.han.system.domain.po.SysRoleMenuPo;
import com.han.system.domain.po.SysUserRolePo;
import com.han.system.mapper.SysMenuMapper;
import com.han.system.mapper.SysRoleMenuMapper;
import com.han.system.mapper.SysUserRoleMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SysMenuServiceImplTest {

    @Test
    void userOneRespectsRoleMenuAssignmentsInsteadOfReceivingAllMenus() {
        SysMenuMapper menuMapper = mock(SysMenuMapper.class);
        SysRoleMenuMapper roleMenuMapper = mock(SysRoleMenuMapper.class);
        SysUserRoleMapper userRoleMapper = mock(SysUserRoleMapper.class);
        SysMenuServiceImpl service = new SysMenuServiceImpl(menuMapper, roleMenuMapper, userRoleMapper);

        SysUserRolePo userRole = new SysUserRolePo();
        userRole.setRoleId(1L);
        SysRoleMenuPo roleMenu = new SysRoleMenuPo();
        roleMenu.setMenuId(10L);
        when(userRoleMapper.selectList(any())).thenReturn(List.of(userRole));
        when(roleMenuMapper.selectList(any())).thenReturn(List.of(roleMenu));

        SysMenuPo root = menu(1L, 0L, "系统管理", "M");
        SysMenuPo allowed = menu(10L, 1L, "系统用户", "C");
        SysMenuPo tenant = menu(20L, 1L, "租户管理", "C");
        when(menuMapper.selectList(any())).thenReturn(List.of(root, allowed, tenant));

        List<SysMenuPo> menus = service.selectMenuTreeByUserId(1L);

        assertThat(menus).hasSize(1);
        assertThat(menus.get(0).getChildren())
                .extracting(SysMenuPo::getMenuName)
                .containsExactly("系统用户");
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
