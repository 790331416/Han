package com.han.api.tenant;

import com.han.api.tenant.domain.TenantVO;
import com.han.common.core.domain.R;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import java.util.List;

/**
 * 租户服务内部调用契约（han-auth / han-system → han-tenant）。
 *
 * <p>服务端要求：han-tenant 必须提供 I 层控制器，类级路径 {@code /inner/tenant}，
 * 标注 {@code @InnerAuth}，返回类型统一为 {@link TenantVO}（本模块的契约类型，
 * 不是 han-tenant 内部的 {@code TenantDTO} 或 {@code com.han.tenant.domain.vo.TenantVO}）。
 * A 层 {@code /tenant/**} 保留给管理端前端，不再承载服务间调用。
 *
 * <p>幂等性：本接口全部方法均为只读 GET，幂等，允许换实例重试。
 * 超时分级：属于「快查询」类，建议连接 3s / 读 10s（由 {@code HttpClientFactoryBean} 统一设置）。
 */
@HttpExchange("/inner/tenant")
public interface TenantServiceClient {

    /**
     * 根据租户ID获取租户信息。
     *
     * <p>服务端：{@code GET /inner/tenant/{tenantId}}。
     */
    @GetExchange("/{tenantId}")
    R<TenantVO> getTenantById(@PathVariable("tenantId") Long tenantId);

    /**
     * 检查租户是否有效（未停用且未过期）。
     *
     * <p>服务端：{@code GET /inner/tenant/check/{tenantId}}。
     * 调用方必须先判 {@code R.isSuccess()} 再取 {@code data}，远程失败一律按「不通过」处理，
     * 不得因为拿不到结果就跳过校验。
     */
    @GetExchange("/check/{tenantId}")
    R<Boolean> checkTenantValid(@PathVariable("tenantId") Long tenantId);

    /**
     * 检查租户用户数是否未超限。
     *
     * <p>服务端：{@code GET /inner/tenant/checkUserLimit/{tenantId}}。
     */
    @GetExchange("/checkUserLimit/{tenantId}")
    R<Boolean> checkUserLimit(@PathVariable("tenantId") Long tenantId);

    /**
     * 查询全部有效租户（含联系人等敏感字段，仅限内部调用）。
     *
     * <p>服务端：{@code GET /inner/tenant/listAllValid}。
     * 登录页的租户下拉不得复用本接口，应由 han-tenant 另行提供只含
     * {@code tenantId + tenantName} 的匿名接口。
     */
    @GetExchange("/listAllValid")
    R<List<TenantVO>> listAllValidTenants();
}
