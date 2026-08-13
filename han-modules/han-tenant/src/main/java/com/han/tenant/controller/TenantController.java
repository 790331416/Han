package com.han.tenant.controller;

import com.han.api.system.SystemClient;
import com.han.common.core.domain.PageResult;
import com.han.common.core.domain.R;
import com.han.common.log.annotation.OperLog;
import com.han.common.security.annotation.RequiresPermission;
import com.han.tenant.domain.dto.TenantDTO;
import com.han.tenant.domain.po.TenantQuotaPo;
import com.han.tenant.domain.query.TenantQuery;
import com.han.tenant.domain.vo.TenantOptionVO;
import com.han.tenant.domain.vo.TenantVO;
import com.han.tenant.security.PlatformTenantGuard;
import com.han.tenant.service.ITenantQuotaService;
import com.han.tenant.service.ITenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 租户管理控制器（A 层）。
 * <p>
 * 全部方法都是平台级管理动作：既要有功能权限注解，也要通过 {@link PlatformTenantGuard} 校验调用者
 * 属于平台租户。跨服务内部调用不要走这里，走 {@code /inner/tenant/**}；
 * 登录页等匿名场景走 {@code /tenant/public/**}。
 */
@RestController
@RequestMapping("/tenant")
@RequiredArgsConstructor
public class TenantController {

    private final ITenantService tenantService;
    private final ITenantQuotaService tenantQuotaService;
    private final SystemClient systemClient;
    private final PlatformTenantGuard platformTenantGuard;

    @RequiresPermission("tenant:list")
    @GetMapping("/list")
    public R<PageResult<TenantDTO>> list(TenantQuery query) {
        platformTenantGuard.assertPlatformTenant();
        return R.ok(tenantService.selectPage(query));
    }

    /**
     * 租户下拉列表。
     * <p>
     * 原先无权限注解且在网关白名单内，返回体含联系人姓名 / 手机号 / 邮箱，未认证即可枚举全平台租户。
     * 现收敛为只返回租户 ID 与名称；匿名调用改用 {@code /tenant/public/options}。
     */
    @RequiresPermission("tenant:list")
    @GetMapping("/all")
    public R<List<TenantOptionVO>> listAll() {
        platformTenantGuard.assertPlatformTenant();
        return R.ok(tenantService.listTenantOptions());
    }

    @RequiresPermission("tenant:query")
    @GetMapping("/{tenantId}")
    public R<TenantDTO> getInfo(@PathVariable Long tenantId) {
        platformTenantGuard.assertPlatformTenant();
        return R.ok(tenantService.selectById(tenantId));
    }

    /**
     * 按域名查询租户详情。匿名的域名解析改用 {@code /tenant/public/domain/{domain}}。
     */
    @RequiresPermission("tenant:query")
    @GetMapping("/domain/{domain}")
    public R<TenantVO> getByDomain(@PathVariable String domain) {
        platformTenantGuard.assertPlatformTenant();
        return R.ok(tenantService.getTenantByDomain(domain));
    }

    /**
     * 新增租户。
     * <p>
     * {@code saveParams = false}：入参 {@link TenantDTO#getAdminPassword()} 是管理员初始明文密码，
     * 默认的入参 JSON 落库会把它写进 sys_oper_log.oper_param。
     */
    @RequiresPermission("tenant:add")
    @PostMapping
    @OperLog(module = "租户管理", type = OperLog.OperType.INSERT, saveParams = false)
    public R<Void> add(@RequestBody @Validated TenantDTO dto) {
        platformTenantGuard.assertPlatformTenant();
        tenantService.insert(dto);
        return R.ok();
    }

    /**
     * 修改租户。{@code saveParams = false} 的原因同 {@link #add(TenantDTO)}。
     */
    @RequiresPermission("tenant:edit")
    @PostMapping("/edit")
    @OperLog(module = "租户管理", type = OperLog.OperType.UPDATE, saveParams = false)
    public R<Void> edit(@RequestBody @Validated TenantDTO dto) {
        platformTenantGuard.assertPlatformTenant();
        tenantService.update(dto);
        return R.ok();
    }

    @RequiresPermission("tenant:edit")
    @PostMapping("/changeStatus")
    @OperLog(module = "租户管理", type = OperLog.OperType.UPDATE)
    public R<Void> changeStatus(@RequestParam Long tenantId, @RequestParam Integer status) {
        platformTenantGuard.assertPlatformTenant();
        tenantService.updateStatus(tenantId, status);
        return R.ok();
    }

    @RequiresPermission("tenant:edit")
    @PostMapping("/syncPackage")
    @OperLog(module = "租户管理", type = OperLog.OperType.GRANT)
    public R<Void> syncPackage(@RequestParam Long tenantId, @RequestParam Long packageId) {
        platformTenantGuard.assertPlatformTenant();
        tenantService.syncTenantPackage(tenantId, packageId);
        return R.ok();
    }

    @RequiresPermission("tenant:remove")
    @PostMapping("/remove/{tenantId}")
    @OperLog(module = "租户管理", type = OperLog.OperType.DELETE)
    public R<Void> remove(@PathVariable Long tenantId) {
        platformTenantGuard.assertPlatformTenant();
        tenantService.deleteTenant(tenantId);
        return R.ok();
    }

    @RequiresPermission("tenant:query")
    @GetMapping("/check/{tenantId}")
    public R<Boolean> checkValid(@PathVariable Long tenantId) {
        platformTenantGuard.assertPlatformTenant();
        return R.ok(tenantService.checkTenantValid(tenantId));
    }

    @RequiresPermission("tenant:query")
    @GetMapping("/checkUserLimit/{tenantId}")
    public R<Boolean> checkUserLimit(@PathVariable Long tenantId) {
        platformTenantGuard.assertPlatformTenant();
        return R.ok(tenantService.checkUserLimit(tenantId));
    }

    @RequiresPermission("tenant:query")
    @GetMapping("/adminUser")
    public R<Long> getAdminUser(@RequestParam Long tenantId) {
        platformTenantGuard.assertPlatformTenant();
        return systemClient.getTenantAdminUserId(tenantId);
    }

    // ==================== 配额管理 ====================

    /**
     * 租户下拉列表（与 {@link #listAll()} 同源，保留供配额页调用）。
     */
    @RequiresPermission("tenant:list")
    @GetMapping("/listAllValid")
    public R<List<TenantOptionVO>> listAllValid() {
        platformTenantGuard.assertPlatformTenant();
        return R.ok(tenantService.listTenantOptions());
    }

    @RequiresPermission("tenant:quota:query")
    @GetMapping("/quota/{tenantId}")
    public R<TenantQuotaPo> getQuota(@PathVariable Long tenantId) {
        platformTenantGuard.assertPlatformTenant();
        return R.ok(tenantQuotaService.getOrDefault(tenantId));
    }

    @RequiresPermission("tenant:quota:edit")
    @PostMapping("/quota/edit")
    @OperLog(module = "租户管理", type = OperLog.OperType.UPDATE)
    public R<Void> editQuota(@RequestBody TenantQuotaPo quota) {
        platformTenantGuard.assertPlatformTenant();
        tenantQuotaService.saveOrUpdate(quota);
        return R.ok();
    }
}
