package com.xuman.tenant.controller;

import com.xuman.common.core.domain.PageResult;
import com.xuman.common.core.domain.R;
import com.xuman.common.security.annotation.RequiresPermission;
import com.xuman.tenant.domain.dto.TenantDTO;
import com.xuman.tenant.domain.vo.TenantVO;
import com.xuman.tenant.service.TenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 租户管理控制器
 */
@RestController
@RequestMapping("/tenant")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService tenantService;

    /**
     * 查询租户列表
     */
    @RequiresPermission("tenant:list")
    @GetMapping("/list")
    public R<PageResult<TenantVO>> list(
            @RequestParam(required = false) String tenantName,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return R.ok(tenantService.listTenants(tenantName, status, pageNum, pageSize));
    }

    /**
     * 查询所有有效租户
     */
    @GetMapping("/all")
    public R<List<TenantVO>> listAll() {
        return R.ok(tenantService.listAllValidTenants());
    }

    /**
     * 获取租户详情
     */
    @RequiresPermission("tenant:query")
    @GetMapping("/{tenantId}")
    public R<TenantVO> getInfo(@PathVariable Long tenantId) {
        return R.ok(tenantService.getTenantById(tenantId));
    }

    /**
     * 根据域名查询租户
     */
    @GetMapping("/domain/{domain}")
    public R<TenantVO> getByDomain(@PathVariable String domain) {
        return R.ok(tenantService.getTenantByDomain(domain));
    }

    /**
     * 创建租户
     */
    @RequiresPermission("tenant:add")
    @PostMapping
    public R<Long> add(@RequestBody @Validated TenantDTO dto) {
        return R.ok(tenantService.createTenant(dto));
    }

    /**
     * 修改租户
     */
    @RequiresPermission("tenant:edit")
    @PostMapping("/edit")
    public R<Void> edit(@RequestBody @Validated TenantDTO dto) {
        tenantService.updateTenant(dto);
        return R.ok();
    }

    /**
     * 删除租户
     */
    @RequiresPermission("tenant:remove")
    @PostMapping("/remove/{tenantId}")
    public R<Void> remove(@PathVariable Long tenantId) {
        tenantService.deleteTenant(tenantId);
        return R.ok();
    }

    /**
     * 修改租户状态
     */
    @RequiresPermission("tenant:edit")
    @PostMapping("/changeStatus")
    public R<Void> changeStatus(@RequestParam Long tenantId, @RequestParam Integer status) {
        tenantService.updateStatus(tenantId, status);
        return R.ok();
    }

    /**
     * 同步套餐
     */
    @RequiresPermission("tenant:edit")
    @PostMapping("/syncPackage")
    public R<Void> syncPackage(@RequestParam Long tenantId, @RequestParam Long packageId) {
        tenantService.syncTenantPackage(tenantId, packageId);
        return R.ok();
    }

    /**
     * 检查租户是否有效
     */
    @GetMapping("/check/{tenantId}")
    public R<Boolean> checkValid(@PathVariable Long tenantId) {
        return R.ok(tenantService.checkTenantValid(tenantId));
    }
}
