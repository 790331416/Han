package com.han.tenant.controller;

import com.han.common.core.domain.R;
import com.han.common.security.annotation.RequiresPermission;
import com.han.tenant.domain.dto.TenantDTO;
import com.han.tenant.domain.query.TenantQuery;
import com.han.tenant.domain.vo.TenantVO;
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
    public R<Void> add(@RequestBody @Validated TenantDTO dto) {
        tenantService.insert(dto);
        return R.ok();
    }

    @RequiresPermission("tenant:edit")
    @PostMapping("/edit")
    public R<Void> edit(@RequestBody @Validated TenantDTO dto) {
        tenantService.update(dto);
        return R.ok();
    }

    @RequiresPermission("tenant:edit")
    @PostMapping("/changeStatus")
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

    @GetMapping("/check/{tenantId}")
    public R<Boolean> checkValid(@PathVariable Long tenantId) {
        return R.ok(tenantService.checkTenantValid(tenantId));
    }

    @GetMapping("/checkUserLimit/{tenantId}")
    public R<Boolean> checkUserLimit(@PathVariable Long tenantId) {
        return R.ok(tenantService.checkUserLimit(tenantId));
    }
}
