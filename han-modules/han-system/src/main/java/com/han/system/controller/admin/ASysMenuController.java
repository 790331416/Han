package com.han.system.controller.admin;

import com.han.common.core.domain.R;
import com.han.common.log.annotation.OperLog;
import com.han.common.security.annotation.AdminAuth;
import com.han.common.security.annotation.PermissionExempt;
import com.han.common.security.context.SecurityContextHolder;
import com.han.system.domain.po.SysMenuPo;
import com.han.system.domain.vo.RouterVO;
import com.han.system.service.ISysMenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 菜单管理 - A层（管理端控制器）
 */
@AdminAuth
@RestController("adminSysMenuController")
@RequestMapping("/system/menu")
@RequiredArgsConstructor
public class ASysMenuController {

    private final ISysMenuService menuService;

    @GetMapping("/routers")
    @PermissionExempt("登录用户获取自身路由菜单，无需特定权限")
    public R<List<RouterVO>> getRouters() {
        Long userId = SecurityContextHolder.getUserId();
        List<SysMenuPo> menus = menuService.selectMenuTreeByUserId(userId);
        return R.ok(menuService.buildRouters(menus));
    }

    @GetMapping("/list")
    @PreAuthorize("@ss.hasAuthority('system:menu:list')")
    public R<List<SysMenuPo>> list(@RequestParam(value = "menuName", required = false) String menuName,
                                   @RequestParam(value = "status", required = false) Integer status) {
        return R.ok(menuService.selectMenuList(menuName, status));
    }

    @GetMapping("/tree")
    @PermissionExempt("菜单树供角色分配菜单弹窗使用，已登录即可访问")
    public R<List<SysMenuPo>> tree() {
        return R.ok(menuService.selectMenuTree());
    }

    @GetMapping("/info/{menuId}")
    @PreAuthorize("@ss.hasAuthority('system:menu:query')")
    public R<SysMenuPo> getInfo(@PathVariable Long menuId) {
        return R.ok(menuService.selectMenuById(menuId));
    }

    @PostMapping
    @PreAuthorize("@ss.hasAuthority('system:menu:add')")
    @OperLog(module = "菜单管理", type = OperLog.OperType.INSERT)
    public R<Void> add(@RequestBody SysMenuPo menu) {
        menuService.insertMenu(menu);
        return R.ok();
    }

    @PostMapping("/edit")
    @PreAuthorize("@ss.hasAuthority('system:menu:edit')")
    @OperLog(module = "菜单管理", type = OperLog.OperType.UPDATE)
    public R<Void> edit(@RequestBody SysMenuPo menu) {
        menuService.updateMenu(menu);
        return R.ok();
    }

    @PostMapping("/remove/{menuId}")
    @PreAuthorize("@ss.hasAuthority('system:menu:remove')")
    @OperLog(module = "菜单管理", type = OperLog.OperType.DELETE)
    public R<Void> remove(@PathVariable Long menuId) {
        menuService.deleteMenuById(menuId);
        return R.ok();
    }
}
