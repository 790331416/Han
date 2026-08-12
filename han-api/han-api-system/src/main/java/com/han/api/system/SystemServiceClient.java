package com.han.api.system;

import com.han.api.system.domain.DataScopeVO;
import com.han.api.system.domain.LoginLogDTO;
import com.han.api.system.domain.SocialBindingVO;
import com.han.api.system.domain.UserPasswordVerifyDTO;
import com.han.api.system.domain.UserTenantVO;
import com.han.api.system.domain.UserTotpVerifyDTO;
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
import java.util.Map;
import java.util.Set;

/**
 * 系统服务内部调用契约（han-auth 等 → han-system）。
 *
 * <p>继承 {@link SystemClient} 以复用租户生命周期那五个方法的<b>唯一</b>定义，
 * 不再各写一份副本。两个接口各自带 {@code @HttpExchange}，扫描注册与调用方注入方式都不变。
 *
 * <p><b>返回值处理约定</b>：{@code R<T>} 有三种失败形态 —— HTTP 非 2xx 抛
 * {@code RestClientException}、HTTP 200 但 {@code code != 200}、{@code data == null}。
 * 调用方必须先判 {@code R.isSuccess()} 再取 {@code data}，仓库里的正确写法参照
 * {@code TenantServiceImpl}（显式检查 {@code isFail()} 并抛业务异常）。
 * 只判 {@code result != null} 或直接 {@code getData()} 都会把远程故障吃成「查无此值」，
 * 在权限相关的接口上就是 fail-open。
 *
 * <p><b>幂等性与重试</b>：所有 {@code GET} 方法幂等，允许换实例重试；
 * {@code POST} 方法全部非幂等（改密钥、改绑定关系、写日志、初始化/清理租户数据），
 * <b>禁止自动重试</b>。底座实现重试策略时不得按 HTTP 状态一刀切。
 */
@HttpExchange("/inner/system")
public interface SystemServiceClient extends SystemClient {

    /**
     * 根据用户ID获取用户信息
     */
    @GetExchange("/user/{userId}")
    R<UserVO> getUserById(@PathVariable("userId") Long userId);

    /**
     * 根据用户名获取用户信息（不限定租户）。
     *
     * <p>与 {@link #getUserByUsername(String, Long)} 打的是同一个服务端 handler
     * （{@code tenantId} 在服务端是 {@code required = false}），只是不带 query 参数。
     * 保留两个重载是为了调用点可读，不是两个不同的端点。
     */
    @GetExchange("/user/info/{username}")
    R<UserVO> getUserByUsername(@PathVariable("username") String username);

    /**
     * 根据用户名+租户ID获取用户信息（多租户登录）
     */
    @GetExchange("/user/info/{username}")
    R<UserVO> getUserByUsername(@PathVariable("username") String username, @RequestParam("tenantId") Long tenantId);

    /**
     * 校验用户密码。
     *
     * <p>取代「把 {@link UserVO#getPassword()} 里的 BCrypt 哈希传回调用方、由调用方自己比对」
     * 的做法：明文密码送进来，比对在 han-system 内部完成，只回布尔值。
     * 远程失败时调用方必须按「校验不通过」处理，不得放行。
     *
     * <p>非幂等语义上是查询，但入参含明文密码所以走 POST + body，避免进 URL 日志。
     */
    @PostExchange("/user/verify-password")
    R<Boolean> verifyPassword(@RequestBody UserPasswordVerifyDTO dto);

    /**
     * 校验用户的 TOTP 动态码。
     *
     * <p>取代 {@link #getTotpSecret(Long)}：种子不出 han-system，调用方只送 userId + 6 位码。
     * 远程失败时调用方必须按「校验不通过」处理。
     */
    @PostExchange("/user/verify-totp")
    R<Boolean> verifyTotpCode(@RequestBody UserTotpVerifyDTO dto);

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
     * 获取用户数据权限范围。
     *
     * <p>取代 {@link #getDataScopeDeptIds(Long)}：把「不限制」从 {@code null} 这个双关值里
     * 拆出来，变成 {@link DataScopeVO#isUnlimited()} 显式表达，拿不到数据时默认就是最严格档。
     */
    @GetExchange("/user/datascope")
    R<DataScopeVO> getDataScope(@RequestParam("userId") Long userId);

    /**
     * 获取用户数据权限部门ID列表。
     *
     * @deprecated {@code null} 在本方法里同时表示「不限制部门范围」（管理员或
     *         {@code data_scope = 1}）和「远程调用失败」。调用方只要漏判 {@code R.code}，
     *         han-system 的一次 {@code R.fail} 就会让普通用户拿到全部部门数据权限。
     *         请改用 {@link #getDataScope(Long)}，语义无歧义且默认 fail-closed。
     */
    @Deprecated
    @GetExchange("/user/datascope/depts")
    R<Set<Long>> getDataScopeDeptIds(@RequestParam("userId") Long userId);

    /**
     * 记录登录日志
     */
    @PostExchange("/loginlog/record")
    R<Void> recordLoginLog(@RequestBody LoginLogDTO dto);

    /**
     * 更新用户 TOTP 密钥（绑定/解绑 2FA）
     *
     * @param userId 用户ID
     * @param secret TOTP 密钥，null 表示解绑
     */
    @PostExchange("/user/totp")
    R<Void> updateTotpSecret(@RequestParam("userId") Long userId, @RequestParam(value = "secret", required = false) String secret);

    /**
     * 获取用户 TOTP 密钥（仅内部调用，用于登录验证）。
     *
     * @deprecated TOTP 明文种子跨进程传输会进入两端的访问日志、链路追踪 payload 与抓包，
     *         而服务间是纯 HTTP 明文。登录校验请改用 {@link #verifyTotpCode(UserTotpVerifyDTO)}，
     *         种子不出 han-system。绑定流程仍走 {@link #updateTotpSecret(Long, String)}，
     *         那条路径上的种子传输需要后续把「生成密钥」也下沉到 han-system 才能一并消除。
     */
    @Deprecated
    @GetExchange("/user/totp/{userId}")
    R<String> getTotpSecret(@PathVariable("userId") Long userId);

    /**
     * 查询同一用户名在各租户下的账号列表。
     */
    @GetExchange("/user/tenants")
    R<List<UserTenantVO>> listUserTenants(@RequestParam("username") String username);

    /**
     * 查询同一用户名在各租户下的账号列表（弱类型版本）。
     *
     * @deprecated {@code Map<String, Object>} 没有目标类型，服务端全局的 {@code Long → String}
     *         序列化会让 {@code tenantId} 到了客户端变成 {@code String}，调用方只能手工转换。
     *         请改用 {@link #listUserTenants(String)}。
     */
    @Deprecated
    @GetExchange("/user/tenants")
    R<List<Map<String, Object>>> getUserTenants(@RequestParam("username") String username);

    /**
     * 查询社交账号绑定的系统用户ID
     *
     * @deprecated 唯一键已按租户隔离，跨租户可能命中多条；请改用 {@link #listSocialBindings(String, String)}。
     *         全仓已无调用方，服务端 {@code ISocialController.getSocialBindUserId} 可与本方法一并下线，
     *         但属于契约能力删除，需要先确认无外部依赖再执行。
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
}
