package com.han.api.system;

import com.han.api.system.domain.TenantInitDto;
import com.han.common.core.domain.R;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.Set;

/**
 * 租户生命周期相关的系统服务内部客户端（han-tenant → han-system）。
 *
 * <p>本接口是这五个方法在契约层的<b>唯一</b>定义。{@link SystemServiceClient} 继承本接口，
 * 因此 han-auth 侧仍能调到同样的方法，但不会再出现第二份声明 —— 此前两个接口各写了一份
 * 完全相同的路径、参数和返回类型，改一处漏一处，编译期发现不了。
 *
 * <p><b>幂等性</b>：{@link #countUsersByTenantId} 与 {@link #getTenantAdminUserId} 是只读 GET，
 * 幂等可重试；{@link #initTenantData}、{@link #cleanupTenantData}、
 * {@link #syncRoleMenusByTenantId} 是非幂等写操作，<b>禁止自动重试</b> —— cleanup 会清空租户的
 * 用户、角色、部门、岗位，重放一次就是二次破坏。
 */
@HttpExchange("/inner/system")
public interface SystemClient {

    /**
     * 统计租户下用户数量。
     */
    @GetExchange("/user/count")
    R<Integer> countUsersByTenantId(@RequestParam("tenantId") Long tenantId);

    /**
     * 初始化租户基础数据。
     */
    @PostExchange("/tenant/init")
    R<Void> initTenantData(@RequestBody TenantInitDto dto);

    /**
     * 清理租户业务数据。
     */
    @PostExchange("/tenant/cleanup")
    R<Void> cleanupTenantData(@RequestParam("tenantId") Long tenantId);

    /**
     * 同步租户角色菜单。
     */
    @PostExchange("/tenant/syncRoleMenus")
    R<Void> syncRoleMenusByTenantId(@RequestParam("tenantId") Long tenantId, @RequestBody Set<Long> menuIds);

    /**
     * 查询租户管理员用户 ID。
     */
    @GetExchange("/tenant/adminUser")
    R<Long> getTenantAdminUserId(@RequestParam("tenantId") Long tenantId);
}
