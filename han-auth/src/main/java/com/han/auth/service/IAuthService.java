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
     */
    LoginVO issueLoginForUser(UserVO user, ClientType clientType, boolean forceChangePassword);

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
}
