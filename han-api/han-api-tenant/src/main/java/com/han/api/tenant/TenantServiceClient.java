package com.han.api.tenant;

import com.han.api.tenant.domain.TenantVO;
import com.han.common.core.domain.R;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/**
 * 租户服务HTTP接口
 */
@HttpExchange("/tenant")
public interface TenantServiceClient {

    /**
     * 根据租户ID获取租户信息
     */
    @GetExchange("/{tenantId}")
    R<TenantVO> getTenantById(@PathVariable("tenantId") Long tenantId);

    /**
     * 检查租户是否有效
     */
    @GetExchange("/check/{tenantId}")
    R<Boolean> checkTenantValid(@PathVariable("tenantId") Long tenantId);

    /**
     * 检查租户用户数是否超限
     */
    @GetExchange("/checkUserLimit/{tenantId}")
    R<Boolean> checkUserLimit(@PathVariable("tenantId") Long tenantId);
}
