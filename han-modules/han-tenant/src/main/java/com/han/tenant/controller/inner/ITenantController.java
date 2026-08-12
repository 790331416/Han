package com.han.tenant.controller.inner;

import com.han.api.tenant.domain.TenantVO;
import com.han.common.core.domain.R;
import com.han.common.security.annotation.InnerAuth;
import com.han.tenant.service.ITenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 租户内部服务控制器（I 层）。
 * <p>
 * 服务端实现 {@code com.han.api.tenant.TenantServiceClient} 契约，调用方为 han-auth 与 han-system。
 * 内部调用不携带用户身份，因此这些方法不能落在带 {@code @RequiresPermission} 的 A 层路径上，
 * 只能由 {@link InnerAuth} 的签名校验把关。返回类型统一使用契约包的 {@link TenantVO}，
 * 不使用模块内展示用的 {@code com.han.tenant.domain.vo.TenantVO}。
 */
@InnerAuth
@RestController
@RequestMapping("/inner/tenant")
@RequiredArgsConstructor
public class ITenantController {

    private final ITenantService tenantService;

    /**
     * 根据租户ID获取租户信息。
     */
    @GetMapping("/{tenantId}")
    public R<TenantVO> getTenantById(@PathVariable("tenantId") Long tenantId) {
        TenantVO tenant = tenantService.getApiTenantById(tenantId);
        if (tenant == null) {
            return R.fail("租户不存在");
        }
        return R.ok(tenant);
    }

    /**
     * 检查租户是否有效（未停用、未过期）。
     */
    @GetMapping("/check/{tenantId}")
    public R<Boolean> checkTenantValid(@PathVariable("tenantId") Long tenantId) {
        return R.ok(tenantService.checkTenantValid(tenantId));
    }

    /**
     * 检查租户用户数是否未超限。
     */
    @GetMapping("/checkUserLimit/{tenantId}")
    public R<Boolean> checkUserLimit(@PathVariable("tenantId") Long tenantId) {
        return R.ok(tenantService.checkUserLimit(tenantId));
    }

    /**
     * 查询所有有效租户。
     */
    @GetMapping("/listAllValid")
    public R<List<TenantVO>> listAllValidTenants() {
        return R.ok(tenantService.listApiValidTenants());
    }
}
