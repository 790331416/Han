package com.han.auth.service;

import com.han.api.system.domain.UserVO;
import com.han.auth.domain.IdentitySelectDTO;
import com.han.auth.domain.IdentityVO;
import com.han.auth.domain.LoginDTO;
import com.han.auth.domain.LoginVO;
import com.han.auth.domain.TenantSimpleVo;
import com.han.common.core.enums.ClientType;

import java.util.List;

/**
 * 认证服务接口
 */
public interface IAuthService {

    /**
     * 登录
     */
    LoginVO login(LoginDTO dto);

    /**
     * 为已完成身份核验的用户签发登录态（密码登录与社交登录共用的公共出口）。
     * <p>包含账号状态校验、租户有效性校验、权限装载、token 签发、互踢与登录日志。
     *
     * <p>仅用于「真无教育身份系统账号」：教育身份查询返回空且确认账号从未接入教育主数据时。
     */
    LoginVO issueLoginForUser(UserVO user, ClientType clientType, boolean forceChangePassword);

    /**
     * 身份感知签发入口（密码登录、社交登录、租户切换等复用）：
     * 0 个有效身份走账号级签发、1 个自动绑定、≥2 个返回 requireIdentity + 一次性身份票据。
     * <p>教育身份查询失败（服务异常或返回失败）时关闭登录，抛「身份服务暂时不可用」。
     */
    LoginVO issueLoginIdentityAware(UserVO user, ClientType clientType, boolean forceChangePassword);

    /**
     * 按指定学校身份签发登录态。
     *
     * <p>{@code identityId} 为 null 时按「单身份自动选择」处理：0 个有效身份抛
     * 「没有有效教育身份」、≥2 个抛「请先选择身份」；非 null 时该校身份必须属于当前账号且有效。
     * 数字校园登录、身份选择、身份切换、Refresh 均复用此出口。
     */
    LoginVO issueLoginForIdentity(UserVO user, ClientType clientType, boolean forceChangePassword, Long identityId);

    /**
     * 刷新Token
     */
    LoginVO refreshToken(String refreshToken);

    /**
     * 登出
     */
    void logout(String token);

    List<TenantSimpleVo> getMyTenants();

    LoginVO switchTenant(Long tenantId, String authorization);

    /**
     * 登录返回 requireIdentity 后，凭一次性身份票据选择学校身份并签发正式 Token。
     */
    LoginVO selectIdentity(IdentitySelectDTO dto);

    /**
     * 返回当前账号仍有效的学校身份列表，并用 {@code current} 标记当前身份。
     */
    List<IdentityVO> getMyIdentities();

    /**
     * 切换到当前账号的另一个有效学校身份：作废旧 Token 与旧身份课堂凭证后换发新 Token。
     */
    LoginVO switchIdentity(Long identityId, String authorization);

    /**
     * 撤销会话：{@code identityId} 为空撤销该账号全部会话与课堂凭证，
     * 指定时只撤销该教育身份对应的会话与课堂 token。
     */
    void revokeSession(Long userId, Long identityId);
}
