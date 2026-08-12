package com.han.tenant.security;

import com.han.common.core.exception.ForbiddenException;
import com.han.common.core.exception.UnauthorizedException;
import com.han.common.security.context.SecurityContextHolder;
import com.han.common.security.domain.LoginUser;
import com.han.tenant.config.HanTenantProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 平台租户边界守卫。
 * <p>
 * 租户与套餐的管理动作原先只校验功能权限（{@code tenant:*}），不校验调用者归属；
 * 而 {@code TenantServiceImpl} 每次访问 {@code sys_tenant} 都显式 {@code TenantHelper.ignore}，
 * 租户插件那层兜底也被主动关掉了。结果是任何拿到 {@code tenant:*} 权限的租户用户
 * 都能列出、停用甚至删除别的租户。此处补上缺失的归属断言。
 * <p>
 * 只用于 A 层管理接口。I 层 {@code /inner/tenant/**} 由内部签名把关，没有登录身份，不得调用本守卫。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlatformTenantGuard {

    private final HanTenantProperties properties;

    /**
     * 断言当前调用者有权做平台级租户管理。
     *
     * @throws UnauthorizedException 未登录
     * @throws ForbiddenException    已登录但不属于平台租户
     */
    public void assertPlatformTenant() {
        if (!properties.isEnforcePlatformBoundary()) {
            return;
        }

        LoginUser user = SecurityContextHolder.getLoginUser();
        if (user == null) {
            throw new UnauthorizedException("未登录或登录已过期");
        }
        if (user.isAdmin()) {
            return;
        }

        Long tenantId = user.getTenantId();
        if (tenantId == null || !tenantId.equals(properties.getPlatformTenantId())) {
            log.warn("非平台租户尝试执行租户管理动作: userId={}, tenantId={}", user.getUserId(), tenantId);
            throw new ForbiddenException("租户管理仅限平台租户操作");
        }
    }
}
