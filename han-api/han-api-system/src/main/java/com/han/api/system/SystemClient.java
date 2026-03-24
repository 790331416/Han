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
 * 租户模块专用的系统服务内部客户端。
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
