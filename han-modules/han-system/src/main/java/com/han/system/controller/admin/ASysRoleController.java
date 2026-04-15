package com.han.system.controller.admin;

import com.han.common.core.domain.PageResult;
import com.han.common.core.domain.R;
import com.han.common.log.annotation.OperLog;
import com.han.common.security.annotation.AdminAuth;
import com.han.common.security.annotation.PermissionExempt;
import com.han.common.security.annotation.RepeatSubmit;
import com.han.common.web.excel.ExcelUtil;
import com.han.system.domain.dto.SysRoleDto;
import com.han.system.domain.po.SysRolePo;
import com.han.system.domain.po.SysUserPo;
import com.han.system.domain.query.SysRoleQuery;
import com.han.system.domain.vo.RoleExportVo;
import com.han.system.service.ISysRoleService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
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

    @RepeatSubmit
    @PostMapping
    @PreAuthorize("@ss.hasAuthority('system:role:add')")
    @OperLog(module = "角色管理", type = OperLog.OperType.INSERT)
    public R<Void> add(@Valid @RequestBody SysRoleDto dto) {
        roleService.insertRole(dto);
        return R.ok();
    }

    @RepeatSubmit
    @PostMapping("/edit")
    @PreAuthorize("@ss.hasAuthority('system:role:edit')")
    @OperLog(module = "角色管理", type = OperLog.OperType.UPDATE)
    public R<Void> edit(@Valid @RequestBody SysRoleDto dto) {
        roleService.updateRole(dto);
        return R.ok();
    }

    @RepeatSubmit
    @PostMapping("/remove/{roleId}")
    @PreAuthorize("@ss.hasAuthority('system:role:remove')")
    @OperLog(module = "角色管理", type = OperLog.OperType.DELETE)
    public R<Void> remove(@PathVariable Long roleId) {
        roleService.deleteRoleById(roleId);
        return R.ok();
    }

    @RepeatSubmit
    @PostMapping("/changeStatus")
    @PreAuthorize("@ss.hasAuthority('system:role:edit')")
    @OperLog(module = "角色管理", type = OperLog.OperType.UPDATE)
    public R<Void> changeStatus(@RequestParam Long roleId, @RequestParam Integer status) {
        roleService.updateRoleStatus(roleId, status);
        return R.ok();
    }

    @GetMapping("/menuIds/{roleId}")
    @PermissionExempt("角色关联菜单ID供角色编辑页回显使用，已登录即可访问")
    public R<List<Long>> getMenuIds(@PathVariable Long roleId) {
        return R.ok(roleService.selectMenuIdsByRoleId(roleId));
    }

    // ==================== 角色分配用户 ====================

    @GetMapping("/authUser/list")
    @PreAuthorize("@ss.hasAuthority('system:role:list')")
    public R<PageResult<SysUserPo>> allocatedList(
            @RequestParam Long roleId,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String phone,
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return R.ok(roleService.selectAllocatedUsers(roleId, username, phone, pageNum, pageSize));
    }

    @GetMapping("/authUser/unallocated")
    @PreAuthorize("@ss.hasAuthority('system:role:list')")
    public R<PageResult<SysUserPo>> unallocatedList(
            @RequestParam Long roleId,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String phone,
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return R.ok(roleService.selectUnallocatedUsers(roleId, username, phone, pageNum, pageSize));
    }

    @RepeatSubmit
    @PostMapping("/authUser/selectAll")
    @PreAuthorize("@ss.hasAuthority('system:role:edit')")
    @OperLog(module = "角色管理", type = OperLog.OperType.GRANT)
    public R<Void> authUserSelectAll(@RequestParam Long roleId, @RequestBody List<Long> userIds) {
        roleService.authUsers(roleId, userIds);
        return R.ok();
    }

    @RepeatSubmit
    @PostMapping("/authUser/cancel")
    @PreAuthorize("@ss.hasAuthority('system:role:edit')")
    @OperLog(module = "角色管理", type = OperLog.OperType.GRANT)
    public R<Void> authUserCancel(@RequestParam Long roleId, @RequestBody List<Long> userIds) {
        roleService.cancelAuthUsers(roleId, userIds);
        return R.ok();
    }

    // ==================== 导出 ====================

    @GetMapping("/export")
    @PreAuthorize("@ss.hasAuthority('system:role:export')")
    @OperLog(module = "角色管理", type = OperLog.OperType.EXPORT)
    public void export(SysRoleQuery query, HttpServletResponse response) throws IOException {
        List<RoleExportVo> list = roleService.selectRoleList(query).stream()
                .map(r -> RoleExportVo.builder()
                        .roleId(String.valueOf(r.getId()))
                        .roleName(r.getRoleName())
                        .roleKey(r.getRoleKey())
                        .roleSort(String.valueOf(r.getRoleSort()))
                        .statusText(r.getStatus() != null && r.getStatus() == 0 ? "正常" : "停用")
                        .createTime(r.getCreateTime() != null ? r.getCreateTime().toString() : "")
                        .build())
                .toList();
        ExcelUtil.exportExcel(response, "角色数据", RoleExportVo.class, list);
    }
}
