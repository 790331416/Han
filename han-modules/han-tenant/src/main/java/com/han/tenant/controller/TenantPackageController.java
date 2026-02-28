package com.han.tenant.controller;

import com.han.common.core.domain.PageResult;
import com.han.common.core.domain.R;
import com.han.common.security.annotation.RequiresPermission;
import com.han.tenant.domain.dto.TenantPackageDTO;
import com.han.tenant.domain.vo.TenantPackageVO;
import com.han.tenant.service.TenantPackageService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/**
 * 租户套餐管理控制器
 */
@RestController
@RequestMapping("/tenant/package")
@RequiredArgsConstructor
public class TenantPackageController {

    private final TenantPackageService tenantPackageService;

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
        return R.ok(tenantPackageService.listPackages(packageName, status, pageNum, pageSize));
    }

    /**
     * 查询所有有效套餐
     */
    @GetMapping("/all")
    public R<List<TenantPackageVO>> listAll() {
        return R.ok(tenantPackageService.listAllValidPackages());
    }

    /**
     * 获取套餐详情
     */
    @RequiresPermission("tenant:package:query")
    @GetMapping("/{packageId}")
    public R<TenantPackageVO> getInfo(@PathVariable Long packageId) {
        return R.ok(tenantPackageService.getPackageById(packageId));
    }

    /**
     * 创建套餐
     */
    @RequiresPermission("tenant:package:add")
    @PostMapping
    public R<Long> add(@RequestBody @Validated TenantPackageDTO dto) {
        return R.ok(tenantPackageService.createPackage(dto));
    }

    /**
     * 修改套餐
     */
    @RequiresPermission("tenant:package:edit")
    @PostMapping("/edit")
    public R<Void> edit(@RequestBody @Validated TenantPackageDTO dto) {
        tenantPackageService.updatePackage(dto);
        return R.ok();
    }

    /**
     * 删除套餐
     */
    @RequiresPermission("tenant:package:remove")
    @PostMapping("/remove/{packageId}")
    public R<Void> remove(@PathVariable Long packageId) {
        tenantPackageService.deletePackage(packageId);
        return R.ok();
    }

    /**
     * 修改套餐状态
     */
    @RequiresPermission("tenant:package:edit")
    @PostMapping("/changeStatus")
    public R<Void> changeStatus(@RequestParam Long packageId, @RequestParam Integer status) {
        tenantPackageService.updateStatus(packageId, status);
        return R.ok();
    }

    /**
     * 获取套餐菜单
     */
    @RequiresPermission("tenant:package:query")
    @GetMapping("/menus/{packageId}")
    public R<Set<Long>> getMenus(@PathVariable Long packageId) {
        return R.ok(tenantPackageService.getPackageMenuIds(packageId));
    }

    /**
     * 更新套餐菜单
     */
    @RequiresPermission("tenant:package:edit")
    @PostMapping("/menus/{packageId}")
    public R<Void> updateMenus(@PathVariable Long packageId, @RequestBody Set<Long> menuIds) {
        tenantPackageService.updatePackageMenus(packageId, menuIds);
        return R.ok();
    }
}
