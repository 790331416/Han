package com.xuman.api.system;

import com.xuman.api.system.domain.UserVO;
import com.xuman.api.system.domain.RoleVO;
import com.xuman.api.system.domain.DeptVO;
import com.xuman.common.core.domain.R;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import java.util.List;
import java.util.Set;

/**
 * 系统服务HTTP接口（@HttpExchange声明式客户端）
 */
@HttpExchange("/system")
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
}
