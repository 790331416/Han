package com.han.tenant.controller;

import com.han.common.core.domain.R;
import com.han.common.security.annotation.PermissionExempt;
import com.han.tenant.domain.vo.TenantOptionVO;
import com.han.tenant.service.ITenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 租户公开信息控制器。
 * <p>
 * 专供登录页等**未认证**场景使用，返回体收敛到 {@link TenantOptionVO}（仅租户 ID 与名称）。
 * 替代原先被网关白名单放行、却返回联系人姓名/手机号/邮箱的 {@code /tenant/all}、
 * {@code /tenant/listAllValid}、{@code /tenant/domain/{domain}}。
 * <p>
 * 网关白名单需放行前缀 {@code /tenant/public/}；本控制器下不得新增任何含 PII 或经营数据的字段。
 */
@RestController
@RequestMapping("/tenant/public")
@RequiredArgsConstructor
public class TenantPublicController {

    private final ITenantService tenantService;

    /**
     * 登录页租户下拉列表。
     */
    @GetMapping("/options")
    @PermissionExempt("登录页未认证时获取租户下拉列表，仅返回租户ID与名称")
    public R<List<TenantOptionVO>> options() {
        return R.ok(tenantService.listTenantOptions());
    }

    /**
     * 按绑定域名解析租户。
     */
    @GetMapping("/domain/{domain}")
    @PermissionExempt("登录页按域名解析所属租户，仅返回租户ID与名称")
    public R<TenantOptionVO> getByDomain(@PathVariable String domain) {
        return R.ok(tenantService.getTenantOptionByDomain(domain));
    }
}
