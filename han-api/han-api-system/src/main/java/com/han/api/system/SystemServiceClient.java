package com.han.api.system;

import com.han.api.system.domain.LoginLogDTO;
import com.han.api.system.domain.TenantInitDto;
import com.han.api.system.domain.UserVO;
import com.han.api.system.domain.RoleVO;
import com.han.api.system.domain.DeptVO;
import com.han.common.core.domain.R;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.List;
import java.util.Set;

/**
 * 系统服务HTTP接口（@HttpExchange声明式客户端）
 */
@HttpExchange("/inner/system")
public interface SystemServiceClient {

    /**
     * 根据用户ID获取用户信息
     */
    @GetExchange("/user/{userId}")
    R<UserVO> getUserById(@PathVariable("userId") Long userId);

    /**
     * 根据用户名获取用户信息
     */
    @GetExchange("/user/info/{username}")
    R<UserVO> getUserByUsername(@PathVariable("username") String username);

    /**
     * 根据用户名+租户ID获取用户信息（多租户登录）
     */
    @GetExchange("/user/info/{username}")
    R<UserVO> getUserByUsername(@PathVariable("username") String username, @RequestParam("tenantId") Long tenantId);

    /**
     * 获取用户角色列表
     */
    @GetExchange("/user/roles")
    R<List<RoleVO>> getRolesByUserId(@RequestParam("userId") Long userId);

    /**
     * 获取用户权限列表
     */
    @GetExchange("/user/permissions")
    R<Set<String>> getPermissionsByUserId(@RequestParam("userId") Long userId);

    /**
     * 获取部门信息
     */
    @GetExchange("/dept/{deptId}")
    R<DeptVO> getDeptById(@PathVariable("deptId") Long deptId);

    /**
     * 获取用户数据权限部门ID列表
     */
    @GetExchange("/user/datascope/depts")
    R<Set<Long>> getDataScopeDeptIds(@RequestParam("userId") Long userId);

    /**
     * 统计租户下用户数量
     */
    @GetExchange("/user/count")
    R<Integer> countUsersByTenantId(@RequestParam("tenantId") Long tenantId);

    /**
     * 初始化租户基础数据（用户/角色/部门）
     */
    @PostExchange("/tenant/init")
    R<Void> initTenantData(@RequestBody TenantInitDto dto);

    /**
     * 清理租户业务数据
     */
    @PostExchange("/tenant/cleanup")
    R<Void> cleanupTenantData(@RequestParam("tenantId") Long tenantId);

    /**
     * 同步租户角色菜单权限（套餐变更时调用）
     */
    @PostExchange("/tenant/syncRoleMenus")
    R<Void> syncRoleMenusByTenantId(@RequestParam("tenantId") Long tenantId, @RequestBody Set<Long> menuIds);

    /**
     * 记录登录日志
     */
    @PostExchange("/loginlog/record")
    R<Void> recordLoginLog(@RequestBody LoginLogDTO dto);
}
