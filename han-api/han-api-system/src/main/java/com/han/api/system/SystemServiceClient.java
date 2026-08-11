package com.han.api.system;

import com.han.api.system.domain.LoginLogDTO;
import com.han.api.system.domain.SocialBindingVO;
import com.han.api.system.domain.TenantInitDto;
import com.han.api.system.domain.UserVO;
import com.han.api.system.domain.RoleVO;
import com.han.api.system.domain.DeptVO;
import com.han.api.system.domain.DigitalCampusUserSyncDTO;
import com.han.common.core.domain.R;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.List;
import java.util.Map;
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
     * 查询租户管理员用户ID
     */
    @GetExchange("/tenant/adminUser")
    R<Long> getTenantAdminUserId(@RequestParam("tenantId") Long tenantId);

    /**
     * 记录登录日志
     */
    @PostExchange("/loginlog/record")
    R<Void> recordLoginLog(@RequestBody LoginLogDTO dto);

    /**
     * 更新用户 TOTP 密钥（绑定/解绑 2FA）
     * @param userId 用户ID
     * @param secret TOTP 密钥，null 表示解绑
     */
    @PostExchange("/user/totp")
    R<Void> updateTotpSecret(@RequestParam("userId") Long userId, @RequestParam(value = "secret", required = false) String secret);

    /**
     * 获取用户 TOTP 密钥（仅内部调用，用于登录验证）
     */
    @GetExchange("/user/totp/{userId}")
    R<String> getTotpSecret(@PathVariable("userId") Long userId);

    @GetExchange("/user/tenants")
    R<List<Map<String, Object>>> getUserTenants(@RequestParam("username") String username);

    /**
     * 查询社交账号绑定的系统用户ID
     *
     * @deprecated 唯一键已按租户隔离，跨租户可能命中多条；请改用 {@link #listSocialBindings(String, String)}。
     */
    @Deprecated
    @GetExchange("/social/bindUser")
    R<Long> getSocialBindUserId(@RequestParam("provider") String provider, @RequestParam("openId") String openId);

    /**
     * 查询社交账号在所有租户下的绑定列表（tenant_id+provider+open_id 唯一）
     */
    @GetExchange("/social/bindings")
    R<List<SocialBindingVO>> listSocialBindings(@RequestParam("provider") String provider, @RequestParam("openId") String openId);

    /**
     * 查询用户在某 provider 下的绑定（一个账号同 provider 只绑一个）
     */
    @GetExchange("/social/binding")
    R<SocialBindingVO> getUserSocialBinding(@RequestParam("userId") Long userId, @RequestParam("provider") String provider);

    /**
     * 查询用户全部社交绑定
     */
    @GetExchange("/social/userBindings")
    R<List<SocialBindingVO>> listUserSocialBindings(@RequestParam("userId") Long userId);

    /**
     * 按 key 读取系统参数值（sys_config），不存在返回空字符串
     */
    @GetExchange("/config/value")
    R<String> getConfigValue(@RequestParam("configKey") String configKey);

    /**
     * 绑定社交账号（tenantId 取被绑定用户所属租户）
     */
    @PostExchange("/social/bind")
    R<Void> bindSocialUser(@RequestParam("userId") Long userId, @RequestParam(value = "tenantId", required = false) Long tenantId,
                            @RequestParam("provider") String provider,
                            @RequestParam("openId") String openId, @RequestParam(value = "accessToken", required = false) String accessToken,
                            @RequestParam(value = "nickname", required = false) String nickname, @RequestParam(value = "avatar", required = false) String avatar);

    /**
     * 解绑社交账号
     */
    @PostExchange("/social/unbind")
    R<Void> unbindSocialUser(@RequestParam("userId") Long userId, @RequestParam("provider") String provider);

    /**
     * 幂等同步数字校园当前用户并返回可签发登录态的 Han 用户。
     */
    @PostExchange("/external/digital-campus/user/sync")
    R<UserVO> syncDigitalCampusUser(@RequestBody DigitalCampusUserSyncDTO dto);
}
