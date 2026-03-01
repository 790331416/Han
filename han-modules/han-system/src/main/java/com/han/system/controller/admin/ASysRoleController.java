package com.han.system.controller.admin;

import com.han.common.core.domain.PageResult;
import com.han.common.core.domain.R;
import com.han.common.security.annotation.AdminAuth;
import com.han.common.security.annotation.PermissionExempt;
import com.han.system.domain.dto.SysRoleDto;
import com.han.system.domain.po.SysRolePo;
import com.han.system.domain.query.SysRoleQuery;
import com.han.system.service.ISysRoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 角色管理 - A层（管理端控制器）
 */
@AdminAuth
@RestController("adminSysRoleController")
@RequestMapping("/system/role")
@RequiredArgsConstructor
public class ASysRoleController {

    private final ISysRoleService roleService;

    @GetMapping("/list")
    @PreAuthorize("@ss.hasAuthority('system:role:list')")
    public R<PageResult<SysRolePo>> list(SysRoleQuery query) {
        return R.ok(roleService.selectRolePage(query));
    }

    @GetMapping("/all")
    @PermissionExempt("角色全量列表供用户分配角色弹窗使用，已登录即可访问")
    public R<List<SysRolePo>> listAll() {
        SysRoleQuery query = new SysRoleQuery();
        query.setStatus(0);
        return R.ok(roleService.selectRoleList(query));
    }

    @GetMapping("/info/{roleId}")
    @PreAuthorize("@ss.hasAuthority('system:role:query')")
    public R<SysRolePo> getInfo(@PathVariable Long roleId) {
        return R.ok(roleService.selectRoleById(roleId));
    }

    @PostMapping
    @PreAuthorize("@ss.hasAuthority('system:role:add')")
    public R<Void> add(@Valid @RequestBody SysRoleDto dto) {
        roleService.insertRole(dto);
        return R.ok();
    }

    @PostMapping("/edit")
    @PreAuthorize("@ss.hasAuthority('system:role:edit')")
    public R<Void> edit(@Valid @RequestBody SysRoleDto dto) {
        roleService.updateRole(dto);
        return R.ok();
    }

    @PostMapping("/remove/{roleId}")
    @PreAuthorize("@ss.hasAuthority('system:role:remove')")
    public R<Void> remove(@PathVariable Long roleId) {
        roleService.deleteRoleById(roleId);
        return R.ok();
    }

    @PostMapping("/changeStatus")
    @PreAuthorize("@ss.hasAuthority('system:role:edit')")
    public R<Void> changeStatus(@RequestParam Long roleId, @RequestParam Integer status) {
        roleService.updateRoleStatus(roleId, status);
        return R.ok();
    }

    @GetMapping("/menuIds/{roleId}")
    @PermissionExempt("角色关联菜单ID供角色编辑页回显使用，已登录即可访问")
    public R<List<Long>> getMenuIds(@PathVariable Long roleId) {
        return R.ok(roleService.selectMenuIdsByRoleId(roleId));
    }
}
