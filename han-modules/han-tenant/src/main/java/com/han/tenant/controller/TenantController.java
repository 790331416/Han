package com.han.tenant.controller;

import com.han.api.system.SystemServiceClient;
import com.han.common.core.domain.R;
import com.han.common.log.annotation.OperLog;
import com.han.common.security.annotation.RequiresPermission;
import com.han.tenant.domain.dto.TenantDTO;
import com.han.tenant.domain.po.TenantQuotaPo;
import com.han.tenant.domain.query.TenantQuery;
import com.han.tenant.domain.vo.TenantVO;
import com.han.tenant.service.ITenantQuotaService;
import com.han.tenant.service.ITenantService;
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

    private final ITenantService tenantService;
    private final ITenantQuotaService tenantQuotaService;
    private final SystemServiceClient systemServiceClient;

    @RequiresPermission("tenant:list")
    @GetMapping("/list")
    public R<List<TenantDTO>> list(TenantQuery query) {
        return R.ok(tenantService.selectList(query));
    }

    @GetMapping("/all")
    public R<List<TenantVO>> listAll() {
        return R.ok(tenantService.listAllValidTenants());
    }

    @RequiresPermission("tenant:query")
    @GetMapping("/{tenantId}")
    public R<TenantDTO> getInfo(@PathVariable Long tenantId) {
        return R.ok(tenantService.selectById(tenantId));
    }

    @GetMapping("/domain/{domain}")
    public R<TenantVO> getByDomain(@PathVariable String domain) {
        return R.ok(tenantService.getTenantByDomain(domain));
    }

    @RequiresPermission("tenant:add")
    @PostMapping
    @OperLog(module = "租户管理", type = OperLog.OperType.INSERT)
    public R<Void> add(@RequestBody @Validated TenantDTO dto) {
        tenantService.insert(dto);
        return R.ok();
    }

    @RequiresPermission("tenant:edit")
    @PostMapping("/edit")
    @OperLog(module = "租户管理", type = OperLog.OperType.UPDATE)
    public R<Void> edit(@RequestBody @Validated TenantDTO dto) {
        tenantService.update(dto);
        return R.ok();
    }

    @RequiresPermission("tenant:edit")
    @PostMapping("/changeStatus")
    @OperLog(module = "租户管理", type = OperLog.OperType.UPDATE)
    public R<Void> changeStatus(@RequestParam Long tenantId, @RequestParam Integer status) {
        tenantService.updateStatus(tenantId, status);
        return R.ok();
    }

    @RequiresPermission("tenant:edit")
    @PostMapping("/syncPackage")
    public R<Void> syncPackage(@RequestParam Long tenantId, @RequestParam Long packageId) {
        tenantService.syncTenantPackage(tenantId, packageId);
        return R.ok();
    }

    @RequiresPermission("tenant:remove")
    @PostMapping("/remove/{tenantId}")
    @OperLog(module = "租户管理", type = OperLog.OperType.DELETE)
    public R<Void> remove(@PathVariable Long tenantId) {
        tenantService.deleteTenant(tenantId);
        return R.ok();
    }

    @GetMapping("/check/{tenantId}")
    public R<Boolean> checkValid(@PathVariable Long tenantId) {
        return R.ok(tenantService.checkTenantValid(tenantId));
    }

    @GetMapping("/checkUserLimit/{tenantId}")
    public R<Boolean> checkUserLimit(@PathVariable Long tenantId) {
        return R.ok(tenantService.checkUserLimit(tenantId));
    }

    @RequiresPermission("tenant:query")
    @GetMapping("/adminUser")
    public R<Long> getAdminUser(@RequestParam Long tenantId) {
        return systemServiceClient.getTenantAdminUserId(tenantId);
    }

    // ==================== 配额管理 ====================

    @GetMapping("/listAllValid")
    public R<List<TenantVO>> listAllValid() {
        return R.ok(tenantService.listAllValidTenants());
    }

    @RequiresPermission("tenant:quota:query")
    @GetMapping("/quota/{tenantId}")
    public R<TenantQuotaPo> getQuota(@PathVariable Long tenantId) {
        TenantQuotaPo quota = tenantQuotaService.getByTenantId(tenantId);
        if (quota == null) {
            quota = new TenantQuotaPo();
            quota.setTenantId(tenantId);
            quota.setUserLimit(-1);
            quota.setStorageLimit(-1L);
            quota.setApiLimit(-1L);
            quota.setUserUsed(0);
            quota.setStorageUsed(0L);
            quota.setApiUsed(0L);
            quota.setResetCycle("monthly");
        }
        // 同步实际用户数
        quota.setUserUsed(tenantService.countTenantUsers(tenantId));
        return R.ok(quota);
    }

    @RequiresPermission("tenant:quota:edit")
    @PostMapping("/quota/edit")
    @OperLog(module = "租户管理", type = OperLog.OperType.UPDATE)
    public R<Void> editQuota(@RequestBody TenantQuotaPo quota) {
        tenantQuotaService.saveOrUpdate(quota);
        return R.ok();
    }
}
