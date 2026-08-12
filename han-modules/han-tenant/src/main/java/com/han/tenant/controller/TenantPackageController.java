package com.han.tenant.controller;

import com.han.common.core.domain.PageResult;
import com.han.common.core.domain.R;
import com.han.common.log.annotation.OperLog;
import com.han.common.security.annotation.RequiresPermission;
import com.han.tenant.domain.dto.TenantPackageDTO;
import com.han.tenant.domain.vo.TenantPackageVO;
import com.han.tenant.security.PlatformTenantGuard;
import com.han.tenant.service.ITenantPackageService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/**
 * 租户套餐管理控制器（A 层）。
 * <p>
 * 套餐是平台经营配置，所有方法都需要功能权限 + 平台租户断言。
 */
@RestController
@RequestMapping("/tenant/package")
@RequiredArgsConstructor
public class TenantPackageController {

    private final ITenantPackageService tenantPackageService;
    private final PlatformTenantGuard platformTenantGuard;

    /**
     * 查询套餐列表
     */
    @RequiresPermission("tenant:package:list")
    @GetMapping("/list")
    public R<PageResult<TenantPackageVO>> list(
            @RequestParam(required = false) String packageName,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        platformTenantGuard.assertPlatformTenant();
        return R.ok(tenantPackageService.listPackages(packageName, status, pageNum, pageSize));
    }

    /**
     * 查询所有有效套餐。原先是本控制器唯一没有权限注解的方法，任意登录用户都能拿到全部套餐及其租户数。
     */
    @RequiresPermission("tenant:package:list")
    @GetMapping("/all")
    public R<List<TenantPackageVO>> listAll() {
        platformTenantGuard.assertPlatformTenant();
        return R.ok(tenantPackageService.listAllValidPackages());
    }

    /**
     * 获取套餐详情
     */
    @RequiresPermission("tenant:package:query")
    @GetMapping("/{packageId}")
    public R<TenantPackageVO> getInfo(@PathVariable Long packageId) {
        platformTenantGuard.assertPlatformTenant();
        return R.ok(tenantPackageService.getPackageById(packageId));
    }

    /**
     * 创建套餐
     */
    @RequiresPermission("tenant:package:add")
    @PostMapping
    @OperLog(module = "租户套餐", type = OperLog.OperType.INSERT)
    public R<Long> add(@RequestBody @Validated TenantPackageDTO dto) {
        platformTenantGuard.assertPlatformTenant();
        return R.ok(tenantPackageService.createPackage(dto));
    }

    /**
     * 修改套餐
     */
    @RequiresPermission("tenant:package:edit")
    @PostMapping("/edit")
    @OperLog(module = "租户套餐", type = OperLog.OperType.UPDATE)
    public R<Void> edit(@RequestBody @Validated TenantPackageDTO dto) {
        platformTenantGuard.assertPlatformTenant();
        tenantPackageService.updatePackage(dto);
        return R.ok();
    }

    /**
     * 删除套餐
     */
    @RequiresPermission("tenant:package:remove")
    @PostMapping("/remove/{packageId}")
    @OperLog(module = "租户套餐", type = OperLog.OperType.DELETE)
    public R<Void> remove(@PathVariable Long packageId) {
        platformTenantGuard.assertPlatformTenant();
        tenantPackageService.deletePackage(packageId);
        return R.ok();
    }

    /**
     * 修改套餐状态
     */
    @RequiresPermission("tenant:package:edit")
    @PostMapping("/changeStatus")
    @OperLog(module = "租户套餐", type = OperLog.OperType.UPDATE)
    public R<Void> changeStatus(@RequestParam Long packageId, @RequestParam Integer status) {
        platformTenantGuard.assertPlatformTenant();
        tenantPackageService.updateStatus(packageId, status);
        return R.ok();
    }

    /**
     * 获取套餐菜单
     */
    @RequiresPermission("tenant:package:query")
    @GetMapping("/menus/{packageId}")
    public R<Set<Long>> getMenus(@PathVariable Long packageId) {
        platformTenantGuard.assertPlatformTenant();
        return R.ok(tenantPackageService.getPackageMenuIds(packageId));
    }

    /**
     * 更新套餐菜单
     */
    @RequiresPermission("tenant:package:edit")
    @PostMapping("/menus/{packageId}")
    @OperLog(module = "租户套餐", type = OperLog.OperType.UPDATE)
    public R<Void> updateMenus(@PathVariable Long packageId, @RequestBody Set<Long> menuIds) {
        platformTenantGuard.assertPlatformTenant();
        tenantPackageService.updatePackageMenus(packageId, menuIds);
        return R.ok();
    }
}
