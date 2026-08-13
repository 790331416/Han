package com.han.tenant.service.support;

import com.han.api.system.SystemClient;
import com.han.common.core.domain.R;
import com.han.common.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 租户角色菜单下发。
 * <p>
 * 收口「把套餐菜单下发到某个租户」这一动作，租户换套餐与套餐菜单回灌两条路径共用。
 * 两条纪律：
 * <ol>
 *   <li>远端返回 {@code R.fail} 不抛异常，必须显式检查 {@link R#isFail()}，否则本地套餐已改、
 *       菜单没同步，留下半一致状态；</li>
 *   <li>空菜单集拒绝下发。远端 syncRoleMenus 是「先删光角色菜单再插入」，
 *       空集会把该租户所有角色的菜单清空，且旧关联被物理删除不可恢复。</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantRoleMenuSynchronizer {

    private final SystemClient systemClient;

    /**
     * 把菜单集合下发到指定租户。
     *
     * @param tenantId 目标租户
     * @param menuIds  套餐菜单集合，不允许为空
     */
    public void sync(Long tenantId, Set<Long> menuIds) {
        if (tenantId == null) {
            throw new BusinessException("租户ID不能为空");
        }
        if (menuIds == null || menuIds.isEmpty()) {
            throw new BusinessException("套餐菜单为空，拒绝下发：该操作会清空租户下全部角色的菜单权限");
        }

        R<Void> result;
        try {
            result = systemClient.syncRoleMenusByTenantId(tenantId, menuIds);
        } catch (Exception ex) {
            log.error("同步租户[{}]角色菜单失败", tenantId, ex);
            throw new BusinessException("同步租户套餐菜单失败: " + ex.getMessage());
        }
        if (result == null || result.isFail()) {
            String reason = result != null && result.getMsg() != null ? result.getMsg() : "同步服务无响应";
            log.error("同步租户[{}]角色菜单失败: {}", tenantId, reason);
            throw new BusinessException("同步租户套餐菜单失败: " + reason);
        }
    }
}
